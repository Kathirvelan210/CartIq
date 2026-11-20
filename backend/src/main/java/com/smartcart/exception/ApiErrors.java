package com.smartcart.exception;
import com.smartcart.dto.Models.ErrorBody;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@RestControllerAdvice
public class ApiErrors {
    private static final Logger log=LoggerFactory.getLogger(ApiErrors.class);
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorBody> uploadLimit() {
        return ResponseEntity.status(413).body(new ErrorBody("PAYLOAD_TOO_LARGE","Submit an intake file of at most 512 KiB."));
    }
    @ExceptionHandler(org.springframework.web.multipart.support.MissingServletRequestPartException.class)
    ResponseEntity<ErrorBody> missingUpload() {
        return ResponseEntity.badRequest().body(new ErrorBody("VALIDATION","Include the document file in the upload."));
    }
    @ExceptionHandler(com.smartcart.recipe.GeminiFailure.class)
    ResponseEntity<ErrorBody> gemini(com.smartcart.recipe.GeminiFailure e) {
        log.warn("ai_failure requestId={} code={} upstreamHttp={} upstreamRequestId={}",MDC.get("requestId"),e.code,
            e.evidence.getFirst("X-Gemini-HTTP-Status"),e.evidence.getFirst("X-Gemini-Request-Id"));
        return ResponseEntity.status(e.status).headers(e.evidence).cacheControl(CacheControl.noStore())
            .body(new ErrorBody(e.code,e.getMessage()));
    }
    @ExceptionHandler(ApiException.class) ResponseEntity<ErrorBody> api(ApiException e){return ResponseEntity.status(e.status).body(new ErrorBody(e.status.name(),e.getMessage()));}
    @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,MissingServletRequestParameterException.class})
    ResponseEntity<ErrorBody> validation(Exception e){return ResponseEntity.badRequest().body(new ErrorBody("VALIDATION","Please check the submitted fields and quantities."));}
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorBody> methodNotSupported(HttpRequestMethodNotSupportedException e){
        return ResponseEntity.status(e.getStatusCode()).headers(e.getHeaders()).body(new ErrorBody("METHOD_NOT_ALLOWED",e.getMessage()));
    }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorBody> unsupportedMedia(HttpMediaTypeNotSupportedException e){
        return ResponseEntity.status(e.getStatusCode()).headers(e.getHeaders()).body(new ErrorBody("UNSUPPORTED_MEDIA_TYPE","Send the request body as JSON."));
    }
    @ExceptionHandler(NoResourceFoundException.class) ResponseEntity<ErrorBody> notFound(NoResourceFoundException e){return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorBody("NOT_FOUND","Resource not found."));}
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<ErrorBody> conflict(Exception e){return ResponseEntity.status(409).body(new ErrorBody("CONFLICT","This record already exists or is in use. Refresh and try again."));}
    @ExceptionHandler(Exception.class) ResponseEntity<ErrorBody> unexpected(Exception e){
        log.error("http_exception requestId={} type={}",MDC.get("requestId"),e.getClass().getSimpleName());
        return ResponseEntity.internalServerError().cacheControl(CacheControl.noStore())
            .body(new ErrorBody("INTERNAL","The request could not be completed. Please try again."));
    }
}
