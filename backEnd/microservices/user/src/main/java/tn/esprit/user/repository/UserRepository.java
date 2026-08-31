package tn.esprit.user.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import tn.esprit.user.entity.Role;
import tn.esprit.user.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByResetToken(String resetToken);

    /**
     * Efface les codes de reinitialisation perimes, en une seule requete.
     *
     * Ecrit en JPQL plutot que lu-puis-sauve ligne par ligne : le nettoyage ne s'interesse
     * a aucun champ des utilisateurs concernes, il n'y a donc aucune raison de les charger
     * en memoire. Renvoie le nombre de lignes touchees.
     */
    @Modifying
    @Query("UPDATE User u SET u.resetToken = NULL, u.resetTokenExpiry = NULL "
         + "WHERE u.resetTokenExpiry IS NOT NULL AND u.resetTokenExpiry < :maintenant")
    int purgerJetonsExpires(@Param("maintenant") LocalDateTime maintenant);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    long countByRole(Role role);
}
