package cl.andesstay.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Establecimiento de la red: un hostal, un complejo de cabañas o un lodge.
 *
 * El caso describe «una red de 20 hostales, cabañas y lodges», y el contrato OpenAPI declara
 * {@code hostalId} como campo obligatorio de la unidad desde el principio. Pero la entidad no
 * existía, así que todas las unidades colgaban de la nada: no se podía responder qué unidades
 * tiene un establecimiento, ni separar la ocupación de uno de la del resto.
 *
 * Es agrupación, no propiedad. El caso NO es un marketplace: no hay anfitriones que publiquen,
 * hay una red que administra su propio inventario desde un solo rol, el Admin.
 */
@Entity
@Table(name = "CATALOG_HOSTALES")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Hostal {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "hostal_seq")
    @SequenceGenerator(name = "hostal_seq", sequenceName = "SEQ_CATALOG_HOSTALES", allocationSize = 1)
    private Long id;

    /**
     * Código de negocio, del estilo H-007. Es el que la red usa para hablar del establecimiento y
     * el que el contrato OpenAPI muestra como ejemplo. El identificador técnico sigue siendo el id
     * numérico, por coherencia con el resto del sistema.
     */
    @NotBlank
    @Size(max = 20)
    @Column(name = "CODIGO", nullable = false, unique = true, length = 20)
    private String codigo;

    @NotBlank
    @Size(max = 150)
    @Column(name = "NOMBRE", nullable = false, length = 150)
    private String nombre;

    @Size(max = 150)
    @Column(name = "COMUNA", length = 150)
    private String comuna;

    @Size(max = 500)
    @Column(name = "DIRECCION", length = 500)
    private String direccion;

    @Column(name = "ACTIVO", nullable = false)
    private Boolean activo = true;

    @Column(name = "CREADO_EN", updatable = false)
    private LocalDateTime creadoEn;
}
