package tn.esprit.user.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La tache planifiee elle-meme. On ne teste pas que Spring la declenche a l'heure —
 * c'est le travail de Spring — mais qu'elle demande bien la purge, et qu'elle la demande
 * avec l'instant present.
 */
@ExtendWith(MockitoExtension.class)
class TokenCleanupJobTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private TokenCleanupJob job;

    @Test
    void asksTheServiceToPurgeWithTheCurrentInstant() {
        LocalDateTime avant = LocalDateTime.now();
        when(userService.purgerJetonsExpires(any())).thenReturn(3);

        job.purgerJetonsExpires();

        ArgumentCaptor<LocalDateTime> instant = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(userService).purgerJetonsExpires(instant.capture());

        // La borne est « maintenant » : un code encore valide ne doit jamais etre efface.
        assertFalse(instant.getValue().isBefore(avant));
        assertTrue(instant.getValue().isBefore(LocalDateTime.now().plusSeconds(5)));
    }

    @Test
    void runsWithoutFailingWhenThereIsNothingToPurge() {
        when(userService.purgerJetonsExpires(any())).thenReturn(0);

        job.purgerJetonsExpires();

        verify(userService).purgerJetonsExpires(any());
    }
}
