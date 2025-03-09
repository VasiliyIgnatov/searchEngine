package searchengine.model;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "site")
@Getter
@Setter
@ToString
public class SiteModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @NonNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "ENUM('INDEXING','INDEXED','FAILED')")
    private Status status;

    @NonNull
    @CreationTimestamp
    @Column(name = "status_time", nullable = false)
    private LocalDateTime statusTime;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @NonNull
    @Column(name = "url", nullable = false, columnDefinition = "VARCHAR(255)")
    private String url;

    @NonNull
    @Column(name = "name", nullable = false, columnDefinition = "VARCHAR(255)")
    private String name;
}
