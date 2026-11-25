package co.nexoeventos.controlador;

import java.util.Map;
import java.util.stream.Collectors;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import co.nexoeventos.dominio.Regla;
import static co.nexoeventos.dominio.Valores.datos;

@RestControllerAdvice
public class ErroresApi {
    @ExceptionHandler(Regla.class)
    ResponseEntity<Map<String,Object>> negocio(Regla error) {
        return ResponseEntity.status(error.estado).body(datos("mensaje",error.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String,Object>> campos(MethodArgumentNotValidException error) {
        Map<String,String> campos=error.getBindingResult().getFieldErrors().stream().collect(Collectors.toMap(
            fallo -> fallo.getField(), fallo -> fallo.getDefaultMessage(),(a,b) -> a));
        return ResponseEntity.badRequest().body(datos("mensaje","Revisa los campos indicados","campos",campos));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,ConstraintViolationException.class})
    ResponseEntity<Map<String,Object>> formato() {
        return ResponseEntity.badRequest().body(datos("mensaje","Uno o más datos tienen un formato inválido"));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<Map<String,Object>> duplicado() {
        return ResponseEntity.status(409).body(datos("mensaje","Ya existe un registro con esa referencia"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String,Object>> archivo() {
        return ResponseEntity.status(413).body(datos("mensaje","El archivo supera el tamaño permitido"));
    }
}
