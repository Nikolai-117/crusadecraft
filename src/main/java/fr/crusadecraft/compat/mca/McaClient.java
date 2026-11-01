package fr.crusadecraft.compat.mca;

import net.minecraft.client.Minecraft;

import java.util.UUID;

/** Les écrans de Comes Alive qu'on ouvre côté client. Jamais chargé sans le mod, ni sur un serveur. */
final class McaClient {
    private McaClient() {}

    /** L'arbre généalogique de Comes Alive : il demande lui-même la famille au serveur. */
    static void openTree(UUID id) {
        Minecraft.getInstance().setScreen(new forge.net.mca.client.gui.FamilyTreeScreen(id));
    }
}
