package org.starlitnova;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.util.HashSet;
import java.util.Set;

class CastResult {
    int hits = 0;
    float rayDistance = 0;
    int side = 0;

    CastResult(int h, float d, int s) {
        hits = h;
        rayDistance = d;
        side = s;
    }
}

public class Renderer extends JPanel {
    // temporary
    public static final int[] MAP_DATA = {
        1, 1, 1, 1, 1, 1, 1, 1,
        1, 0, 0, 0, 0, 0, 0, 1,
        1, 0, 0, 0, 0, 3, 0, 1,
        1, 0, 0, 0, 0, 0, 0, 1,
        1, 0, 2, 0, 4, 4, 0, 1,
        1, 0, 0, 0, 4, 0, 0, 1,
        1, 0, 3, 0, 0, 0, 0, 1,
        1, 1, 1, 1, 1, 1, 1, 1
    };

    // temporary
    public static final Color[] MAP_COLORS = {
        new Color(255, 255, 255),
        new Color(255, 0, 0),
        new Color(0, 255, 0),
        new Color(0, 0, 255)
    };

    private static final Color FLOOR_COLOR = new Color(110, 65, 45);
    private static final Color ROOF_COLOR = new Color(45, 45, 45);

    private float playerX = 2.0f, playerY = 2.0f;
    private float playerRot = 0.0f;
    private final float FOV = (float)Math.toRadians(70);

    private Set<Integer> pressedKeys = new HashSet<>();

    private static Color multiplyColor(Color c, float val) {
        int red = (int)Math.max(0, Math.min(255, c.getRed() * val));
        int green = (int)Math.max(0, Math.min(255, c.getGreen() * val));
        int blue = (int)Math.max(0, Math.min(255, c.getBlue() * val));

        return new Color(red, green, blue);
    }

    public Renderer() {
        this.setPreferredSize(new Dimension(800, 600));

        this.setFocusable(true);
        this.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                pressedKeys.add(e.getKeyCode());
            }

            public void keyReleased(KeyEvent e) {
                pressedKeys.remove(e.getKeyCode());
            }
        });

        // 16 is ~60fps (1000ms / 16ms ~ 60 frames)
        Timer timer = new Timer(16, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                update();
                repaint();
            }
        });


        timer.start();
    }

    private void update() {
        // rotation
        if (pressedKeys.contains(KeyEvent.VK_LEFT)) {
            rotatePlayer(-0.1f); // rotate left
        }

        if (pressedKeys.contains(KeyEvent.VK_RIGHT)) {
            rotatePlayer(0.1f); // rotate right
        }

        // movement
        if (pressedKeys.contains(KeyEvent.VK_W)) {
            movePlayerForward(0.1f); // forwards
        }

        if (pressedKeys.contains(KeyEvent.VK_S)) {
            movePlayerForward(-0.1f); // backwards
        }
    }

    public void movePlayerForward(float dist) {
        playerX += Math.cos(playerRot) * dist;
        playerY += Math.sin(playerRot) * dist;
    }

    public void rotatePlayer(float angle) {
        playerRot += angle;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        render((Graphics2D) g);
    }

    private void render(Graphics2D g) {
        int width = getWidth();
        int height = getHeight();

        int halfHeight = height / 2;

        GradientPaint roofGrad = new GradientPaint(0, 0, ROOF_COLOR, 0, halfHeight, multiplyColor(ROOF_COLOR, 0.2f));
        GradientPaint floorGrad = new GradientPaint(0, 0, multiplyColor(FLOOR_COLOR, 0.2f), 0, height, FLOOR_COLOR);

        g.setPaint(roofGrad);
        g.fillRect(0, 0, width, halfHeight);
        g.setPaint(floorGrad);
        g.fillRect(0, halfHeight, width, height - halfHeight); // use height - halfHeight in case theres an odd number of pixels

        // ray caster
        for (int x = 0; x < width; x++) {
            float camX = 2 * x / (float)width - 1;

            float rayAngle = (playerRot - FOV / 2) + ((float)x / width) * FOV;
            CastResult res = castRay(playerX, playerY, rayAngle);

            // multiply by the cosine of the angle offset to fix how human perspective works
            float correctedDist = res.rayDistance * (float)Math.cos(rayAngle - playerRot);

            int lineHeight = (int)(height / correctedDist);

            float fogRatio = Math.max(0.0f, 255.0f - correctedDist * 25.0f) / 255.0f;

            g.setColor(multiplyColor(MAP_COLORS[res.hits - 1], fogRatio));
            g.drawLine(x, (height - lineHeight) / 2, x, (height + lineHeight) / 2);
        }
    }

    private CastResult castRay(float sX, float sY, float angle) {
        float dirX = (float)Math.cos(angle);
        float dirY = (float)Math.sin(angle);

        int mapX = (int) sX;
        int mapY = (int) sY;

        float sideDistX, sideDistY;

        float deltaDistX = Math.abs(1 / dirX);
        float deltaDistY = Math.abs(1 / dirY);
        float perpWallDist;

        int stepX, stepY;

        if (dirX < 0) {
            stepX = -1;
            sideDistX = (sX - mapX) * deltaDistX;
        } else {
            stepX = 1;
            sideDistX = (mapX + 1.0f - sX) * deltaDistX;
        }

        if (dirY < 0) {
            stepY = -1;
            sideDistY = (sY - mapY) * deltaDistY;
        } else {
            stepY = 1;
            sideDistY = (mapY + 1.0f - sY) * deltaDistY;
        }

        boolean hit = false;
        int side = 0;
        int value = 0;
        while (!hit) {
            if (sideDistX < sideDistY) {
                sideDistX += deltaDistX;
                mapX += stepX;
                side = 0;
            } else {
                sideDistY += deltaDistY;
                mapY += stepY;
                side = 1;
            }

            if (MAP_DATA[mapX + mapY * 8] > 0) {
                hit = true;
                value = MAP_DATA[mapX + mapY * 8];
            }
        }

        if (side == 0) {
            perpWallDist = (mapX - sX + (1 - stepX) / 2) / dirX;
        } else {
            perpWallDist = (mapY - sY + (1 - stepY) / 2) / dirY;
        }

        CastResult res = new CastResult(value, perpWallDist, side);
        return res;
    }
}
