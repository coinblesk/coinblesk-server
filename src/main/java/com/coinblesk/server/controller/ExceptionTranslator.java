package com.coinblesk.server.controller;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.METHOD_NOT_ALLOWED;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE;

import java.util.Date;

import javax.servlet.http.HttpServletRequest;

import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import lombok.Data;

@ControllerAdvice
public class ExceptionTranslator {

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorDTO> handleExceptions(Exception e, HttpServletRequest request) {
		// idea by http://blog.sizovs.net/spring-rest-exception-handler/
		ResponseStatus annotation = AnnotationUtils.findAnnotation(e.getClass(), ResponseStatus.class);
		HttpStatus responseStatus = INTERNAL_SERVER_ERROR;
		String message = e.getLocalizedMessage();

		if (annotation != null) {
			if(annotation.value() != null) {
				responseStatus = annotation.value();
			}
			if(annotation.reason() != null) {
				message = annotation.reason();
			}
		} else if (e instanceof AuthenticationException) {
			responseStatus = UNAUTHORIZED;
		} else if (e instanceof AccessDeniedException) {
			responseStatus = FORBIDDEN;
		} else if (e instanceof MethodArgumentNotValidException
				|| e instanceof HttpMessageConversionException
				|| e instanceof ServletRequestBindingException) {
			responseStatus = BAD_REQUEST;
		} else if (e instanceof HttpRequestMethodNotSupportedException) {
			responseStatus = METHOD_NOT_ALLOWED;
		} else if (e instanceof HttpMediaTypeException) {
			responseStatus = UNSUPPORTED_MEDIA_TYPE;
		}

		ErrorDTO result = new ErrorDTO();
		result.setTimestamp(new Date());
		result.setStatus(responseStatus.value());
		result.setError(responseStatus.getReasonPhrase());
		result.setException(e.getClass().getSimpleName());
		result.setMessage(message);
		result.setMethod(request.getMethod());
		result.setPath(request.getRequestURI());

		return new ResponseEntity<>(result, responseStatus);
	}

	@Data
	public class ErrorDTO {
		private Date timestamp;
		private int status;
		private String error;
		private String exception;
		private String message;
		private String method;
		private String path;
	}

}
