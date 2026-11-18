package com.patobloxfruits.bot.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class QrCodeServiceTest {

    @Test
    void geraPngValido() throws Exception {
        byte[] png = new QrCodeService().toPng("00020101021126580014BR.GOV.BCB.PIX0136teste5204000053039865802BR6304ABCD");

        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(img).isNotNull();
        assertThat(img.getWidth()).isEqualTo(400);
    }
}
