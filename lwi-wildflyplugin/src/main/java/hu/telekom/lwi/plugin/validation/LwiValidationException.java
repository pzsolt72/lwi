package hu.telekom.lwi.plugin.validation;

public class LwiValidationException extends Exception {

	public LwiValidationException() {
		super();
	}

	public LwiValidationException(String message) {
		super(message);
	}

	public LwiValidationException(Throwable cause) {
		super(cause);
	}

	public LwiValidationException(String message, Throwable cause) {
		super(message, cause);
	}

	public LwiValidationException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
