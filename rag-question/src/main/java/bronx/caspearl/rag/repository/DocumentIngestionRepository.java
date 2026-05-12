package bronx.caspearl.rag.repository;

import bronx.caspearl.rag.entity.DocumentIngestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentIngestionRepository extends JpaRepository<DocumentIngestion, UUID> {

    Optional<DocumentIngestion> findTopBySourceNameAndStatusOrderByCreatedAtDesc(
            String sourceName, DocumentIngestion.IngestionStatus status);

    List<DocumentIngestion> findBySourceNameOrderByCreatedAtDesc(String sourceName);

    boolean existsBySourceNameAndStatus(String sourceName, DocumentIngestion.IngestionStatus status);
}
