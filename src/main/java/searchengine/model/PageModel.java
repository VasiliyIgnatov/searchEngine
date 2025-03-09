package searchengine.model;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.*;

@Entity
@Getter
@Setter
@ToString
@Table(name = "page", indexes = {
        @Index(name = "idx_path", columnList = "path")},
        uniqueConstraints = {@UniqueConstraint(columnNames = {"path", "site_id"})})
public class PageModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private SiteModel site;

    @NonNull
    @Column(name = "path", nullable = false, columnDefinition = "VARCHAR(255)")
    private String path;

    @NonNull
    @Column(name = "code", nullable = false)
    private int code;

    @NonNull
    @Column(name = "content", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;
}
