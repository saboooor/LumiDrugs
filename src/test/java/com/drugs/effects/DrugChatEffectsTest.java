package com.drugs.effects;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DrugChatEffectsTest {
    @Test void drugFamiliesProduceDistinctSpeechAndExcludeWithdrawal() {
        assertEquals("h-hello", DrugChatEffects.transformWord("hello", DrugChatEffects.style("cocaine")));
        assertEquals("hellooo", DrugChatEffects.transformWord("hello", DrugChatEffects.style("joint")));
        assertEquals("hello...", DrugChatEffects.transformWord("hello", DrugChatEffects.style("lsd")));
        assertNull(DrugChatEffects.style("withdrawal/cocaine"));
        assertNull(DrugChatEffects.style("vape"));
    }

    @Test void chatPreservesFormattingAndStopsAfterCleanup() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        AsyncChatEvent event = mock(AsyncChatEvent.class);
        when(event.getPlayer()).thenReturn(player);
        Component original = Component.text("Hello, friend! Third.", net.kyori.adventure.text.format.NamedTextColor.GOLD);
        when(event.message()).thenReturn(original);
        var listener = new DrugChatEffects();
        DrugChatEffects.trackSalvia(player, 200);
        listener.onChat(event);
        var changed = org.mockito.ArgumentCaptor.forClass(Component.class);
        verify(event).message(changed.capture());
        assertEquals(Component.text("Hello..., friend...! Third.", net.kyori.adventure.text.format.NamedTextColor.GOLD), changed.getValue().compact());
        clearInvocations(event);
        listener.onQuit(new PlayerQuitEvent(player, Component.empty(), org.bukkit.event.player.PlayerQuitEvent.QuitReason.DISCONNECTED));
        listener.onChat(event);
        verify(event, never()).message(any(Component.class));
    }
}
