package searchengine.model;

import lombok.*;

import javax.persistence.*;

@Entity
@Table(name = "lemma", uniqueConstraints = {@UniqueConstraint(columnNames = {"lemma", "site_id"})})
@Getter
@Setter
@ToString
public class LemmaModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private SiteModel site;

    @NonNull
    @Column(name = "lemma", nullable = false, columnDefinition = "VARCHAR(255)")
    private String lemma;

    @NonNull
    @Column(name = "frequency", nullable = false)
    private int frequency;
}
