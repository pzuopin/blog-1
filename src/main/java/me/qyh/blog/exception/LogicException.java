package me.qyh.blog.exception;

import me.qyh.blog.message.Message;

public class LogicException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private Message logicMessage;

	public LogicException(Message message) {
		this.logicMessage = message;
	}

	public LogicException(String code, String defaultMessage, Object... args) {
		this(new Message(code, defaultMessage, args));
	}

	public LogicException(String code, Object... args) {
		this(new Message(code, null, args));
	}

	public Message getLogicMessage() {
		return logicMessage;
	}

	@Override
	public Throwable fillInStackTrace() {
		return this;
	}
}
