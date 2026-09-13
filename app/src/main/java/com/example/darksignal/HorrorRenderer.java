package com.example.darksignal;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.SystemClock;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Locale;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class HorrorRenderer implements GLSurfaceView.Renderer {
    public interface GameEvents {
        void onPickup();
        void onMonsterAwake();
        void onDeath();
        void onWin();
        void onGameStart();
    }

    public enum GameState { READY, PLAYING, GAME_OVER, WON }

    private static final float CELL = 1.65f;
    private static final float WALL_H = 2.65f;
    private static final float EYE_H = 1.18f;
    private static final float PLAYER_R = 0.22f;
    private static final float MOVE_SPEED = 2.15f;
    private static final float MONSTER_SPEED = 0.82f;

    private static final String[] MAP = new String[]{
            "#########################",
            "#.#...#.............#...#",
            "#.#.#.#.#########.#.#...#",
            "#.#.#.............#...#.#",
            "#.#.#######.###########.#",
            "#...#...#...#.........#.#",
            "#.###.#.#.###.#.#####.###",
            "#.....#.#...........#...#",
            "#######.#.#############.#",
            "#.....#.#.#.............#",
            "#.###...###.###########.#",
            "#...#.#...#...#.......#.#",
            "###.#.###.###.#.#######.#",
            "#.#.#.........#.#...#...#",
            "#.#.###.#.#.#.#.#.#.#.###",
            "#.....#.....#.....#.....#",
            "#########################"
    };

    private static final int[][] FUSE_CELLS = new int[][]{
            {23, 5}, {3, 5}, {13, 1}
    };
    private static final int START_X = 1;
    private static final int START_Z = 1;

    private final GameEvents events;
    private final Random random = new Random(1337);
    private final float[] projection = new float[16];
    private final float[] view = new float[16];
    private final float[] vp = new float[16];
    private final float[] model = new float[16];
    private final float[] mvp = new float[16];
    private FloatBuffer cubeBuffer;

    private int program;
    private int aPos;
    private int aNormal;
    private int uMvp;
    private int uModel;
    private int uEye;
    private int uForward;
    private int uColor;
    private int uEmissive;
    private int uFlicker;

    private volatile GameState state = GameState.READY;
    private volatile int fuseCount = 0;
    private final boolean[] fuseTaken = new boolean[3];
    private volatile String transientMessage = "";
    private volatile long messageUntilMs = 0;
    private volatile float elapsedSeconds = 0f;

    private float px = START_X * CELL;
    private float pz = START_Z * CELL;
    private float yawDeg = 180f;
    private float pitchDeg = 0f;
    private volatile float moveForward = 0f;
    private volatile float moveStrafe = 0f;

    private boolean monsterActive = false;
    private float monsterX;
    private float monsterZ;
    private boolean monsterWarned = false;

    private long lastNanos;
    private float worldTime = 0f;
    private float bobPhase = 0f;

    public HorrorRenderer(GameEvents events) {
        this.events = events;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.003f, 0.005f, 0.012f, 1f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        aPos = GLES20.glGetAttribLocation(program, "aPosition");
        aNormal = GLES20.glGetAttribLocation(program, "aNormal");
        uMvp = GLES20.glGetUniformLocation(program, "uMVP");
        uModel = GLES20.glGetUniformLocation(program, "uModel");
        uEye = GLES20.glGetUniformLocation(program, "uEye");
        uForward = GLES20.glGetUniformLocation(program, "uForward");
        uColor = GLES20.glGetUniformLocation(program, "uBaseColor");
        uEmissive = GLES20.glGetUniformLocation(program, "uEmissive");
        uFlicker = GLES20.glGetUniformLocation(program, "uFlicker");
        cubeBuffer = makeBuffer(CUBE_VERTICES);
        lastNanos = System.nanoTime();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        float ratio = (float) width / Math.max(1, height);
        Matrix.perspectiveM(projection, 0, 67f, ratio, 0.08f, 50f);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        long now = System.nanoTime();
        float dt = Math.min((now - lastNanos) / 1_000_000_000f, 0.045f);
        lastNanos = now;
        worldTime += dt;

        if (state == GameState.PLAYING) update(dt);
        renderScene();
    }

    private void update(float dt) {
        elapsedSeconds += dt;
        float yaw = (float) Math.toRadians(yawDeg);
        float fx = (float) Math.sin(yaw);
        float fz = -(float) Math.cos(yaw);
        float rx = (float) Math.cos(yaw);
        float rz = (float) Math.sin(yaw);

        float inputLen = (float) Math.sqrt(moveForward * moveForward + moveStrafe * moveStrafe);
        float nf = moveForward;
        float ns = moveStrafe;
        if (inputLen > 1f) {
            nf /= inputLen;
            ns /= inputLen;
        }
        float dx = (fx * nf + rx * ns) * MOVE_SPEED * dt;
        float dz = (fz * nf + rz * ns) * MOVE_SPEED * dt;

        if (Math.abs(dx) + Math.abs(dz) > 0.0001f) {
            float nx = px + dx;
            if (!blocked(nx, pz)) px = nx;
            float nz = pz + dz;
            if (!blocked(px, nz)) pz = nz;
            bobPhase += dt * 9f * Math.min(1f, inputLen);
        }

        for (int i = 0; i < FUSE_CELLS.length; i++) {
            if (fuseTaken[i]) continue;
            float fxp = FUSE_CELLS[i][0] * CELL;
            float fzp = FUSE_CELLS[i][1] * CELL;
            if (dist(px, pz, fxp, fzp) < 0.72f) {
                fuseTaken[i] = true;
                fuseCount++;
                transientMessage = fuseCount < 3 ?
                        "ПРЕДОХРАНИТЕЛЬ " + fuseCount + "/3" :
                        "ПИТАНИЕ ВОССТАНОВЛЕНО. ВЕРНИСЬ К ШЛЮЗУ.";
                messageUntilMs = SystemClock.uptimeMillis() + 2600;
                if (events != null) events.onPickup();

                if (fuseCount == 2 && !monsterActive) activateMonster();
            }
        }

        if (monsterActive) updateMonster(dt);

        if (fuseCount == 3) {
            float ex = START_X * CELL;
            float ez = START_Z * CELL;
            if (dist(px, pz, ex, ez) < 0.58f) {
                state = GameState.WON;
                moveForward = moveStrafe = 0f;
                transientMessage = "";
                if (events != null) events.onWin();
            }
        }
    }

    private void activateMonster() {
        monsterActive = true;
        monsterWarned = true;
        float yaw = (float) Math.toRadians(yawDeg);
        float fx = (float) Math.sin(yaw);
        float fz = -(float) Math.cos(yaw);
        monsterX = px - fx * 8.5f;
        monsterZ = pz - fz * 8.5f;
        transientMessage = "ТЫ ЗДЕСЬ НЕ ОДИН.";
        messageUntilMs = SystemClock.uptimeMillis() + 3000;
        if (events != null) events.onMonsterAwake();
    }

    private void updateMonster(float dt) {
        float dx = px - monsterX;
        float dz = pz - monsterZ;
        float d = (float) Math.sqrt(dx * dx + dz * dz);
        if (d > 0.001f) {
            monsterX += dx / d * MONSTER_SPEED * dt;
            monsterZ += dz / d * MONSTER_SPEED * dt;
        }

        if (d > 16f) {
            float yaw = (float) Math.toRadians(yawDeg);
            float fx = (float) Math.sin(yaw);
            float fz = -(float) Math.cos(yaw);
            monsterX = px - fx * 11f + (random.nextFloat() - 0.5f) * 2f;
            monsterZ = pz - fz * 11f + (random.nextFloat() - 0.5f) * 2f;
        }

        if (d < 0.72f) {
            state = GameState.GAME_OVER;
            moveForward = moveStrafe = 0f;
            transientMessage = "";
            if (events != null) events.onDeath();
        }
    }

    private boolean blocked(float x, float z) {
        return solidAt(x - PLAYER_R, z - PLAYER_R) ||
                solidAt(x + PLAYER_R, z - PLAYER_R) ||
                solidAt(x - PLAYER_R, z + PLAYER_R) ||
                solidAt(x + PLAYER_R, z + PLAYER_R);
    }

    private boolean solidAt(float x, float z) {
        int gx = Math.round(x / CELL);
        int gz = Math.round(z / CELL);
        if (gz < 0 || gz >= MAP.length) return true;
        if (gx < 0 || gx >= MAP[gz].length()) return true;
        return MAP[gz].charAt(gx) == '#';
    }

    private void renderScene() {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glUseProgram(program);

        float yaw = (float) Math.toRadians(yawDeg);
        float pitch = (float) Math.toRadians(pitchDeg);
        float fx = (float) (Math.sin(yaw) * Math.cos(pitch));
        float fy = (float) Math.sin(pitch);
        float fz = (float) (-Math.cos(yaw) * Math.cos(pitch));
        float bob = state == GameState.PLAYING ? (float) Math.sin(bobPhase) * 0.025f : 0f;
        float eyeY = EYE_H + bob;

        Matrix.setLookAtM(view, 0,
                px, eyeY, pz,
                px + fx, eyeY + fy, pz + fz,
                0f, 1f, 0f);
        Matrix.multiplyMM(vp, 0, projection, 0, view, 0);

        float monsterDist = monsterActive ? dist(px, pz, monsterX, monsterZ) : 99f;
        float flicker = 0.92f + 0.08f * (float) Math.sin(worldTime * 5.5f);
        if (monsterDist < 5.0f) {
            flicker *= 0.50f + 0.50f * Math.abs((float) Math.sin(worldTime * 17.3f));
        }

        GLES20.glUniform3f(uEye, px, eyeY, pz);
        GLES20.glUniform3f(uForward, fx, fy, fz);
        GLES20.glUniform1f(uFlicker, flicker);

        cubeBuffer.position(0);
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 24, cubeBuffer);
        GLES20.glEnableVertexAttribArray(aPos);
        cubeBuffer.position(3);
        GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, 24, cubeBuffer);
        GLES20.glEnableVertexAttribArray(aNormal);

        float worldW = (MAP[0].length() - 1) * CELL;
        float worldD = (MAP.length - 1) * CELL;
        drawCube(worldW * 0.5f, -0.07f, worldD * 0.5f,
                worldW + CELL, 0.14f, worldD + CELL,
                0f, 0.15f, 0.16f, 0.18f, 0f);
        drawCube(worldW * 0.5f, WALL_H + 0.07f, worldD * 0.5f,
                worldW + CELL, 0.14f, worldD + CELL,
                0f, 0.035f, 0.045f, 0.06f, 0f);

        for (int z = 0; z < MAP.length; z++) {
            String row = MAP[z];
            for (int x = 0; x < row.length(); x++) {
                if (row.charAt(x) == '#') {
                    float tint = ((x + z) % 3 == 0) ? 0.19f : 0.16f;
                    drawCube(x * CELL, WALL_H * 0.5f, z * CELL,
                            CELL * 0.98f, WALL_H, CELL * 0.98f,
                            0f, tint * 0.78f, tint * 0.90f, tint, 0f);
                } else if (((x * 19 + z * 31) % 37) == 0) {
                    drawCube(x * CELL, WALL_H - 0.10f, z * CELL,
                            0.10f, 0.08f, 0.55f,
                            0f, 0.55f, 0.05f, 0.06f, 0.85f);
                }
            }
        }

        for (int i = 0; i < FUSE_CELLS.length; i++) {
            if (!fuseTaken[i]) {
                float pulse = 0.80f + 0.20f * (float) Math.sin(worldTime * 4f + i);
                drawCube(FUSE_CELLS[i][0] * CELL, 0.58f, FUSE_CELLS[i][1] * CELL,
                        0.36f, 0.95f, 0.20f,
                        worldTime * 35f, 0.05f, 0.85f * pulse, 0.95f, 1.7f);
                drawCube(FUSE_CELLS[i][0] * CELL, 0.12f, FUSE_CELLS[i][1] * CELL,
                        0.72f, 0.08f, 0.72f,
                        0f, 0.02f, 0.50f, 0.62f, 1.2f);
            }
        }

        float exitGlow = fuseCount == 3 ? 1.6f : 0.15f;
        float exitR = fuseCount == 3 ? 0.05f : 0.55f;
        float exitG = fuseCount == 3 ? 0.95f : 0.03f;
        drawCube(START_X * CELL, 1.15f, (START_Z - 0.46f) * CELL,
                CELL * 0.65f, 2.15f, 0.12f,
                0f, exitR, exitG, 0.08f, exitGlow);

        if (monsterActive && state != GameState.WON) drawMonster();

        GLES20.glDisableVertexAttribArray(aPos);
        GLES20.glDisableVertexAttribArray(aNormal);
    }

    private void drawMonster() {
        float ang = (float) Math.toDegrees(Math.atan2(px - monsterX, -(pz - monsterZ)));
        drawCube(monsterX, 0.90f, monsterZ,
                0.48f, 1.65f, 0.30f,
                ang, 0.015f, 0.012f, 0.018f, 0.12f);
        drawCube(monsterX, 1.86f, monsterZ,
                0.42f, 0.48f, 0.38f,
                ang, 0.012f, 0.010f, 0.014f, 0.10f);

        float a = (float) Math.toRadians(ang);
        float faceX = (float) Math.sin(a);
        float faceZ = -(float) Math.cos(a);
        float sideX = (float) Math.cos(a);
        float sideZ = (float) Math.sin(a);
        for (float side : new float[]{-1f, 1f}) {
            float ex = monsterX + faceX * 0.205f + sideX * 0.09f * side;
            float ez = monsterZ + faceZ * 0.205f + sideZ * 0.09f * side;
            drawCube(ex, 1.91f, ez,
                    0.055f, 0.045f, 0.045f,
                    ang, 0.95f, 0.015f, 0.015f, 3.0f);
        }
    }

    private void drawCube(float cx, float cy, float cz,
                          float sx, float sy, float sz,
                          float rotYDeg, float r, float g, float b, float emissive) {
        Matrix.setIdentityM(model, 0);
        Matrix.translateM(model, 0, cx, cy, cz);
        if (rotYDeg != 0f) Matrix.rotateM(model, 0, rotYDeg, 0f, 1f, 0f);
        Matrix.scaleM(model, 0, sx, sy, sz);
        Matrix.multiplyMM(mvp, 0, vp, 0, model, 0);
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0);
        GLES20.glUniformMatrix4fv(uModel, 1, false, model, 0);
        GLES20.glUniform4f(uColor, r, g, b, 1f);
        GLES20.glUniform1f(uEmissive, emissive);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 36);
    }

    public void startGame() {
        if (state == GameState.PLAYING) return;
        resetInternal(true);
        if (events != null) events.onGameStart();
    }

    public void restartAndPlay() {
        resetInternal(true);
        if (events != null) events.onGameStart();
    }

    private void resetInternal(boolean play) {
        px = START_X * CELL;
        pz = START_Z * CELL;
        yawDeg = 180f;
        pitchDeg = 0f;
        moveForward = moveStrafe = 0f;
        fuseCount = 0;
        for (int i = 0; i < fuseTaken.length; i++) fuseTaken[i] = false;
        monsterActive = false;
        monsterWarned = false;
        elapsedSeconds = 0f;
        bobPhase = 0f;
        transientMessage = play ? "НАЙДИ 3 ПРЕДОХРАНИТЕЛЯ. ПОТОМ ВЕРНИСЬ СЮДА." : "";
        messageUntilMs = SystemClock.uptimeMillis() + 3200;
        state = play ? GameState.PLAYING : GameState.READY;
        lastNanos = System.nanoTime();
    }

    public void setMove(float strafe, float forward) {
        this.moveStrafe = clamp(strafe, -1f, 1f);
        this.moveForward = clamp(forward, -1f, 1f);
    }

    public void addLook(float dxPixels, float dyPixels) {
        if (state != GameState.PLAYING) return;
        yawDeg += dxPixels * 0.17f;
        pitchDeg -= dyPixels * 0.14f;
        pitchDeg = clamp(pitchDeg, -42f, 42f);
    }

    public GameState getState() { return state; }
    public int getFuseCount() { return fuseCount; }
    public float getElapsedSeconds() { return elapsedSeconds; }
    public boolean isMonsterActive() { return monsterActive; }
    public String getTransientMessage() {
        return SystemClock.uptimeMillis() < messageUntilMs ? transientMessage : "";
    }

    public String getObjectiveText() {
        if (fuseCount < 3) return "ПРЕДОХРАНИТЕЛИ: " + fuseCount + "/3";
        return "ШЛЮЗ ОТКРЫТ — ВЕРНИСЬ К НАЧАЛУ";
    }

    public String getTimeText() {
        int total = Math.max(0, (int) elapsedSeconds);
        return String.format(Locale.US, "%d:%02d", total / 60, total % 60);
    }

    private static float dist(float ax, float az, float bx, float bz) {
        float dx = ax - bx;
        float dz = az - bz;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static FloatBuffer makeBuffer(float[] data) {
        ByteBuffer bb = ByteBuffer.allocateDirect(data.length * 4).order(ByteOrder.nativeOrder());
        FloatBuffer fb = bb.asFloatBuffer();
        fb.put(data).position(0);
        return fb;
    }

    private static int createProgram(String vs, String fs) {
        int v = compileShader(GLES20.GL_VERTEX_SHADER, vs);
        int f = compileShader(GLES20.GL_FRAGMENT_SHADER, fs);
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, v);
        GLES20.glAttachShader(p, f);
        GLES20.glLinkProgram(p);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) throw new RuntimeException("OpenGL link error: " + GLES20.glGetProgramInfoLog(p));
        GLES20.glDeleteShader(v);
        GLES20.glDeleteShader(f);
        return p;
    }

    private static int compileShader(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) throw new RuntimeException("OpenGL shader error: " + GLES20.glGetShaderInfoLog(s));
        return s;
    }

    private static final String VERTEX_SHADER =
            "uniform mat4 uMVP;\n" +
            "uniform mat4 uModel;\n" +
            "attribute vec3 aPosition;\n" +
            "attribute vec3 aNormal;\n" +
            "varying vec3 vWorldPos;\n" +
            "varying vec3 vNormal;\n" +
            "void main(){\n" +
            "  vec4 world = uModel * vec4(aPosition,1.0);\n" +
            "  vWorldPos = world.xyz;\n" +
            "  vNormal = normalize(mat3(uModel) * aNormal);\n" +
            "  gl_Position = uMVP * vec4(aPosition,1.0);\n" +
            "}\n";

    private static final String FRAGMENT_SHADER =
            "precision mediump float;\n" +
            "uniform vec3 uEye;\n" +
            "uniform vec3 uForward;\n" +
            "uniform vec4 uBaseColor;\n" +
            "uniform float uEmissive;\n" +
            "uniform float uFlicker;\n" +
            "varying vec3 vWorldPos;\n" +
            "varying vec3 vNormal;\n" +
            "void main(){\n" +
            "  vec3 delta = vWorldPos - uEye;\n" +
            "  float d = length(delta);\n" +
            "  vec3 dir = normalize(delta);\n" +
            "  float cone = smoothstep(0.80, 0.965, dot(normalize(uForward), dir));\n" +
            "  float facing = 0.35 + 0.65 * max(dot(normalize(vNormal), normalize(uEye - vWorldPos)), 0.0);\n" +
            "  float attenuation = 1.0 / (1.0 + 0.045*d*d);\n" +
            "  float light = 0.030 + cone * attenuation * 4.2 * facing * uFlicker;\n" +
            "  vec3 c = uBaseColor.rgb * (light + uEmissive);\n" +
            "  float fog = smoothstep(6.0, 18.5, d);\n" +
            "  c = mix(c, vec3(0.002,0.004,0.010), fog);\n" +
            "  gl_FragColor = vec4(c,1.0);\n" +
            "}\n";

    private static final float[] CUBE_VERTICES = new float[]{
            -0.5f,-0.5f, 0.5f, 0,0,1,   0.5f,-0.5f, 0.5f, 0,0,1,   0.5f, 0.5f, 0.5f, 0,0,1,
            -0.5f,-0.5f, 0.5f, 0,0,1,   0.5f, 0.5f, 0.5f, 0,0,1,  -0.5f, 0.5f, 0.5f, 0,0,1,
             0.5f,-0.5f,-0.5f, 0,0,-1, -0.5f,-0.5f,-0.5f,0,0,-1, -0.5f, 0.5f,-0.5f,0,0,-1,
             0.5f,-0.5f,-0.5f, 0,0,-1, -0.5f, 0.5f,-0.5f,0,0,-1,  0.5f, 0.5f,-0.5f,0,0,-1,
            -0.5f,-0.5f,-0.5f,-1,0,0,  -0.5f,-0.5f, 0.5f,-1,0,0,  -0.5f, 0.5f, 0.5f,-1,0,0,
            -0.5f,-0.5f,-0.5f,-1,0,0,  -0.5f, 0.5f, 0.5f,-1,0,0,  -0.5f, 0.5f,-0.5f,-1,0,0,
             0.5f,-0.5f, 0.5f, 1,0,0,   0.5f,-0.5f,-0.5f, 1,0,0,   0.5f, 0.5f,-0.5f, 1,0,0,
             0.5f,-0.5f, 0.5f, 1,0,0,   0.5f, 0.5f,-0.5f, 1,0,0,   0.5f, 0.5f, 0.5f, 1,0,0,
            -0.5f, 0.5f, 0.5f, 0,1,0,   0.5f, 0.5f, 0.5f, 0,1,0,   0.5f, 0.5f,-0.5f, 0,1,0,
            -0.5f, 0.5f, 0.5f, 0,1,0,   0.5f, 0.5f,-0.5f, 0,1,0,  -0.5f, 0.5f,-0.5f, 0,1,0,
            -0.5f,-0.5f,-0.5f,0,-1,0,   0.5f,-0.5f,-0.5f,0,-1,0,   0.5f,-0.5f, 0.5f,0,-1,0,
            -0.5f,-0.5f,-0.5f,0,-1,0,   0.5f,-0.5f, 0.5f,0,-1,0,  -0.5f,-0.5f, 0.5f,0,-1,0
    };
}
