#!/usr/bin/perl
use strict;
use warnings;
use Getopt::Long;
use Pod::Usage;

#input params
my($help)=0;
my($DEBUG)=1;
my($outAppRolesFile)="application-roles.properties";
my($outAppUsersFile)="application-users.properties";
my($inUsersDatFile)="users.yml";
my($inServerDatFile)="server.yml";
my($inServiceDatFileMask)="*service.yml";

#constants
my $REALM="ApplicationRealm";

#data
my %USERS = ();
my %USER_CONSUMED_SERVICES = ();
my @CLI = ();
my $JKS_PASSWD;
my $JKS_PATH;
my %HOSTS = ();

push @CLI, "# run with: ./jboss-cli.sh --commands=embed-server,run-batch\ --file=lwi.cli";
push @CLI, '# logger config';
push @CLI, '/subsystem=logging/size-rotating-file-handler=LWI_LOG_MESSAGE:add(autoflush=true,file={"path"=>"/home/dockeri/logs/lwi_audit.log"},append=true,suffix=.yyyy-MM-dd,formatter="%d{HH:mm:ss,SSS} %-5p [%c] (%t) %s%E%n",level="INFO",rotate-size="100M",max-backup-index="10")';
push @CLI, '/subsystem=logging/logger=LWI_LOG_MESSAGE:add(level=DEBUG,handlers=["LWI_LOG_MESSAGE"])';
push @CLI, '/subsystem=logging/logger=LWI_LOG_MESSAGE:write-attribute(name=use-parent-handlers,value=false)';
push @CLI, '/subsystem=logging/size-rotating-file-handler=LWI_SEPARATE:add(autoflush=true,file={"path"=>"/home/dockeri/logs/lwi_app.log"},append=true,suffix=.yyyy-MM-dd,formatter="%d{HH:mm:ss,SSS} %-5p [%c] (%t) %s%E%n",level="INFO",rotate-size="100M",max-backup-index="10")';
push @CLI, '/subsystem=logging/logger=LWI_SEPARATE:add(level=DEBUG,handlers=["LWI_SEPARATE"])';
push @CLI, '/subsystem=logging/logger=LWI_SEPARATE:write-attribute(name=use-parent-handlers,value=true)';
push @CLI, '# general filter config';
push @CLI, "/subsystem=undertow/server=default-server/host=default-host/filter-ref=FULL_REQUEST_LIMIT:add(predicate=path-prefix('/lwi'))";
push @CLI, '# service level filter config';

GetOptions("userDat=s" =>\$inUsersDatFile,
           "serverDat=s" =>\$inServerDatFile,
           "help" =>\$help,
           "appRoles=s" =>\$outAppRolesFile,
           "appUsers=s" =>\$outAppUsersFile,
           "serviceDatMask=s" =>\$inServiceDatFileMask);

pod2usage(0) if $help;
printParams();

my @serviceDatFiles = glob "$inServiceDatFileMask";
checkInputFiles();

readUsersDatFile();
readServerDatFile();

foreach my $serviceDatFile (@serviceDatFiles) {
  debugMsg("Service file: $serviceDatFile");
  readServiceDatFile($serviceDatFile);
}

createAppUsersFile();
createAppRolesFile();
createCliBatchFile();

sub checkInputFiles {
  if (! -f $inUsersDatFile) {
    errorMsg("No users dat file found: $inUsersDatFile");
  }
  if (! -f $inServerDatFile) {
    errorMsg("No server dat file found: $inServerDatFile");
  }
  if (@serviceDatFiles == 0) {
    errorMsg("No service.yml files found with path mask: $inServiceDatFileMask");
  }
}

sub readUsersDatFile {
  my($datFile);
  my($userName, $userPass);
  open($datFile, $inUsersDatFile) || die "Cannot open $inUsersDatFile\n";
  while(<$datFile>) {
    if (/-\sname:\s+(\S+)*/) {
      $userName=$1;
    }
    if (/password:\s+(\S+)\s*$/) {
      #if username not empty then 2 paswords for one user
      $USERS{$userName}=$1;
      $userName="";
    }
  }
  close($datFile);
  if ($DEBUG) {
    foreach $userName (sort keys %USERS) {
      print "$userName/$USERS{$userName}\n";
    }
  }
}

sub readServerDatFile {
  my($datFile);
  my($section) = "";
  open($datFile, $inServerDatFile) || die "Cannot open $inServerDatFile\n";
  while(<$datFile>) {
    if (/^(\S+):\s*/) {
      $section=$1;
      if ($section !~ m/^(logger|load|jks)$/) {
        die "Invalid section in $inServerDatFile: $section\n";
      }
    }
    if ($section eq "jks" and /password:\s+(\S+)\s*$/) {
      $JKS_PASSWD=$1;
    }
    if ($section eq "jks" and /path:\s+(\S+)\s*$/) {
      $JKS_PATH=$1;
    }
  }
  close($datFile);
  push @CLI, '# security realm config';
  push @CLI, '/core-service=management/security-realm=UndertowRealm:add()';
  push @CLI, '/core-service=management/security-realm=UndertowRealm/authentication=truststore:add(keystore-path="'.$JKS_PATH.'",keystore-password="'.$JKS_PASSWD.'")';
  push @CLI, '/core-service=management/security-realm=UndertowRealm/server-identity=ssl:add(keystore-path="'.$JKS_PATH.'",keystore-password="'.$JKS_PASSWD.'")';
}

sub readServiceDatFile {
  my($serviceDatFile) = shift;
  my($datFile);
  my($name, $uri, $shortUri, $consumers);
  my($servProto, $servHost, $servPort, $servResource);
  my($urlOk) = 0;
  my @params = ();
  open($datFile, $serviceDatFile) || die "Cannot open $serviceDatFile\n";
  while(<$datFile>) {
    next if /^service:/;
    if (/\sconsumers:\s+\[(\S+)\]\s*$/) {
      foreach my $c (split(/,/,$1)) {
        if (defined($USER_CONSUMED_SERVICES{$c})) {
          $USER_CONSUMED_SERVICES{$c}.=",".$shortUri;
        } else {
          $USER_CONSUMED_SERVICES{$c}=$shortUri;
        }
      }
      next;
    }
    if (/-\sname:\s+(\S+)\s*$/) {
      $name=$1;
      next;
    }
    if (/\suri:\s+(\S+)\s*$/) {
      $uri=$1;
      $shortUri=$uri;
      $shortUri=~s/^lwi\///;
      next;
    }
    if (/\sbackEndServiceUrl:\s+(\w+):\/\/([^:]+):(\d+)(\/\S+)\s*$/) {
      $servProto = lc($1);
      $servHost = $2;
      $servPort = $3;
      $servResource = $4;
      $urlOk = 1;
    }
    if (/\s(\w+):\s+(\S+)\s*$/) {
      push @params, "\"$1\" => \"$2\"";
    }
  }
  if (!$urlOk) {
    die "Service url syntax not recognized (proto://host:port/resource) in $serviceDatFile\n";
  }
  push @CLI, "/subsystem=undertow/server=default-server/host=default-host/location=\"/$uri\":add(handler=$name-proxy)";
  push @CLI, "/subsystem=undertow/configuration=handler/reverse-proxy=$name-proxy:add(max-request-time=30000,connections-per-thread=2)";
  if ($servProto eq "http") {
    push @CLI, "/subsystem=undertow/configuration=handler/reverse-proxy=$name-proxy/host=${servHost}1:add(outbound-socket-binding=$servHost,path='$servResource',instance-id=$servHost-route1)";
  } else {
    push @CLI, "/subsystem=undertow/configuration=handler/reverse-proxy=$name-proxy/host=${servHost}1:add(scheme=$servProto,security-realm=UndertowRealm,outbound-socket-binding=$servHost,path='$servResource',instance-id=$servHost-route1)";
  }
  push @CLI, "/subsystem=undertow/server=default-server/host=default-host/filter-ref=$name:add(predicate=path-prefix('/".$uri."'))";
  push @CLI, "/subsystem=undertow/configuration=filter/custom-filter=$name:add(class-name=hu.telekom.lwi.plugin.LwiHandler, module=hu.telekom.lwi,parameters = {".(join(',',@params))."})";
 
  if (!exists($HOSTS{"$servHost:$servPort"})) {
    push @CLI, "/socket-binding-group=standard-sockets/remote-destination-outbound-socket-binding=$servHost:add(host=$servHost, port=$servPort)";
    $HOSTS{"$servHost:$servPort"}=1;
  } 
  close($datFile);
}

sub createAppUsersFile {
  my($md5cmd);
  my($md5);
  open(my $propFile,">$outAppUsersFile") || die "Cannot write $outAppUsersFile\n";
  foreach my $userName (sort keys %USERS) {
      $md5cmd="echo $userName:$REALM:$USERS{$userName} | md5sum | cut -d' ' -f 1";
      $md5=qx/$md5cmd/; 
      print $propFile "$userName=$md5";
  }
  close($propFile);
}

sub createAppRolesFile {
  open(my $propFile,">$outAppRolesFile") || die "Cannot write $outAppRolesFile\n";
  foreach my $userName (sort keys %USER_CONSUMED_SERVICES) {
      print $propFile "$userName=$USER_CONSUMED_SERVICES{$userName}\n";
  }
  close($propFile);
}

sub createCliBatchFile {
  my $outCliBatchFile = "lwi.cli";
  open(my $cliFile,">$outCliBatchFile") || die "Cannot write $outCliBatchFile\n";
  foreach my $line (@CLI) {
      print $cliFile "$line\n";
  }
  close($cliFile);
}

sub msg {
  my($msg)=@_;
  print "$msg\n";
}

sub errorMsg {
  msg(@_);
  exit 1;
}

sub debugMsg {
  if ($DEBUG) {
    msg(@_);
  }
}

sub printParams {
  debugMsg("using the following parameter values:");
  debugMsg("-appRoles = $outAppRolesFile");
  debugMsg("-appUsers = $outAppUsersFile");
  debugMsg("-userDat = $inUsersDatFile");
  debugMsg("-serverDat = $inServerDatFile");
  debugMsg("-serviceDatMask = $inServiceDatFileMask");
}

__END__

=head1 NAME

create_users_roles

=head1 SYNOPSIS

sample [options] [file ...]

Options:
   -help            brief help message
   -man             full documentation

=head1 OPTIONS

=over 8

=item B<-help>

Print a brief help message and exits.

=item B<-man>

Prints the manual page and exits.

=back

=head1 DESCRIPTION

    B<This program> will read the given input file(s) and do something
    useful with the contents thereof.

=cut
