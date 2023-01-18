package com.nettakrim.spyglass_astronomy.mixin;


import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.nettakrim.spyglass_astronomy.SpyglassAstronomyClient;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@Mixin(ChatHud.class)
public class ChatHudMixin {
    @Inject(at = @At("TAIL"), method = "addMessage(Lnet/minecraft/text/Text;IIZ)V")
    public void onChat(Text message, int messageId, int timestamp, boolean refresh, CallbackInfo ci) {
        String messageString = message.getString();
        int sgaIndex = messageString.indexOf("sga:");
        if (sgaIndex == -1) return;
        
        String data = messageString.substring(sgaIndex+4);
        int firstIndex = data.indexOf("|");
        if (firstIndex == -1) return;
        int secondIndex = data.indexOf("|", firstIndex+1);
        data = data.substring(0, secondIndex == -1 ? firstIndex : secondIndex);
        if (data.charAt(1) != '_') return;


        switch (data.charAt(0)) {
            case 'c':
                //constellation shared with sga:c_Name|AAAA|
                if (secondIndex == -1) return;
                String constellationName = data.substring(2, firstIndex);
                String constellationData = data.substring(firstIndex+1, secondIndex);

                Text constellationText = getClickHere(
                    String.format("[Spyglass Astronomy] |/[Click Here]| to add Constellation \"%s\"", constellationName),
                    "/sga:admin add constellation "+constellationData+" "+constellationName
                );
                
                SpyglassAstronomyClient.say(constellationText);
                break;
            case 's':
                //star shared with sga:s_Name|index|
                if (secondIndex == -1) return;
                String starName = data.substring(2, firstIndex);
                int starIndex = Integer.parseInt(data.substring(firstIndex+1, secondIndex));

                Text starText = getClickHere(
                    String.format("[Spyglass Astronomy] |/[Click Here]| to add Star \"%s\"", starName),
                    "/sga:rename star "+Integer.toString(starIndex)+" "+starName
                );
                
                SpyglassAstronomyClient.say(starText);
                break;
            case 'p':
                //planets shared with sga:p_Name|index|
                if (secondIndex == -1) return;
                String orbitingBodyName = data.substring(2, firstIndex);
                int orbitingBodyIndex = Integer.parseInt(data.substring(firstIndex+1, secondIndex));

                Text orbitingBodyText = getClickHere(
                    String.format("[Spyglass Astronomy] |/[Click Here]| to add Planet \"%s\"", orbitingBodyName),
                    "/sga:rename planet "+Integer.toString(orbitingBodyIndex)+" "+orbitingBodyName
                );

                SpyglassAstronomyClient.say(orbitingBodyText);
                break;           
        }
    }

    private Text getClickHere(String formatText, String command) {
        String[] parts = formatText.split("\\|");

        MutableText text = Text.literal("");
        for (String string : parts) {
            if (string.charAt(0) == '/') {
                text.append(Text.literal(string.substring(1)).setStyle(Style.EMPTY
                    .withClickEvent(
                        new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)
                    )
                    .withColor(Formatting.GREEN)
                ));
            } else {
                text.append(Text.literal(string));
            }
        }
        
        return text;
    }
}
