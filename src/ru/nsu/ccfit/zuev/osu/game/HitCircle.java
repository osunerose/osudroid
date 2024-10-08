package ru.nsu.ccfit.zuev.osu.game;

import android.graphics.PointF;

import com.reco1l.osu.graphics.Modifiers;

import org.anddev.andengine.entity.scene.Scene;
import org.anddev.andengine.entity.sprite.Sprite;

import ru.nsu.ccfit.zuev.osu.Config;
import ru.nsu.ccfit.zuev.osu.RGBColor;
import ru.nsu.ccfit.zuev.osu.ResourceManager;
import ru.nsu.ccfit.zuev.osu.Utils;
import ru.nsu.ccfit.zuev.osu.scoring.ResultType;
import ru.nsu.ccfit.zuev.skins.OsuSkin;

public class HitCircle extends GameObject {
    private final Sprite circle;
    private final Sprite overlay;
    private final Sprite approachCircle;
    private final RGBColor color = new RGBColor();
    private CircleNumber number;
    private GameObjectListener listener;
    private Scene scene;
    private int soundId;
    private int sampleSet;
    private int addition;
    //private PointF pos;
    private float radius;
    private float passedTime;
    private float time;
    private boolean isFirstNote;
    private boolean kiai;

    public HitCircle() {
        // Getting sprites from sprite pool
        circle = new Sprite(0, 0, ResourceManager.getInstance().getTexture("hitcircle"));
        circle.setAlpha(0);
        overlay = new Sprite(0, 0, ResourceManager.getInstance().getTexture("hitcircleoverlay"));
        overlay.setAlpha(0);
        approachCircle = new Sprite(0, 0, ResourceManager.getInstance().getTexture("approachcircle"));
    }

    public void init(final GameObjectListener listener, final Scene pScene,
                     final PointF pos, final float time, final float r, final float g,
                     final float b, final float scale, int num, final int sound, final String tempSound, final boolean isFirstNote) {
        // Storing parameters into fields
        //Log.i("note-ini", time + "s");
        this.replayObjectData = null;
        this.pos = pos;
        this.listener = listener;
        this.scene = pScene;
        this.soundId = sound;
        this.sampleSet = 0;
        this.addition = 0;
        // TODO: 外部音效文件支持
        this.time = time;
        this.isFirstNote = isFirstNote;
        passedTime = 0;
        startHit = false;
        kiai = GameHelper.isKiai();
        color.set(r, g, b);

        if (!Utils.isEmpty(tempSound)) {
            final String[] group = tempSound.split(":");
            this.sampleSet = Integer.parseInt(group[0]);
            this.addition = Integer.parseInt(group[1]);
        }

        // Calculating position of top/left corner for sprites and hit radius
        radius = Utils.toRes(128) * scale / 2;
        radius *= radius;

        // Initializing sprites
        //circle.setPosition(rpos.x, rpos.y);
        circle.setColor(r, g, b);
        circle.setScale(scale);
        circle.setAlpha(0);
        Utils.putSpriteAnchorCenter(pos, circle);

        //overlay.setPosition(rpos.x, rpos.y);
        overlay.setScale(scale);
        overlay.setAlpha(0);
        Utils.putSpriteAnchorCenter(pos, overlay);

        //approachCircle.setPosition(rpos.x, rpos.y);
        approachCircle.setColor(r, g, b);
        approachCircle.setScale(scale * 3);
        approachCircle.setAlpha(0);
        Utils.putSpriteAnchorCenter(pos, approachCircle);
        if (GameHelper.isHidden()) {
            approachCircle.setVisible(Config.isShowFirstApproachCircle() && this.isFirstNote);
        }

        // and getting new number from sprite pool
        num += 1;
        if (OsuSkin.get().isLimitComboTextLength()) {
            num %= 10;
        }
        number = GameObjectPool.getInstance().getNumber(num);
        number.init(pos, GameHelper.getScale());
        number.setAlpha(0);

        float fadeInDuration;

        if (GameHelper.isHidden()) {
            fadeInDuration = time * 0.4f * GameHelper.getTimeMultiplier();
            float fadeOutDuration = time * 0.3f * GameHelper.getTimeMultiplier();

            number.registerEntityModifier(Modifiers.sequence(
                    Modifiers.fadeIn(fadeInDuration),
                    Modifiers.fadeOut(fadeOutDuration)
            ));
            overlay.registerEntityModifier(Modifiers.sequence(
                    Modifiers.fadeIn(fadeInDuration),
                    Modifiers.fadeOut(fadeOutDuration)
            ));
            circle.registerEntityModifier(Modifiers.sequence(
                    Modifiers.fadeIn(fadeInDuration),
                    Modifiers.fadeOut(fadeOutDuration)
            ));
        } else {
            // Preempt time can go below 450ms. Normally, this is achieved via the DT mod which uniformly speeds up all animations game wide regardless of AR.
            // This uniform speedup is hard to match 1:1, however we can at least make AR>10 (via mods) feel good by extending the upper linear function above.
            // Note that this doesn't exactly match the AR>10 visuals as they're classically known, but it feels good.
            // This adjustment is necessary for AR>10, otherwise TimePreempt can become smaller leading to hitcircles not fully fading in.
            fadeInDuration = 0.4f * Math.min(1, time / 0.45f) * GameHelper.getTimeMultiplier();

            circle.registerEntityModifier(Modifiers.fadeIn(fadeInDuration));
            overlay.registerEntityModifier(Modifiers.fadeIn(fadeInDuration));
            number.registerEntityModifier(Modifiers.fadeIn(fadeInDuration));
        }

        if (approachCircle.isVisible()) {
            approachCircle.registerEntityModifier(Modifiers.alpha(Math.min(fadeInDuration * 2, time * GameHelper.getTimeMultiplier()), 0, 0.9f));
            approachCircle.registerEntityModifier(Modifiers.scale(time * GameHelper.getTimeMultiplier(), scale * 3, scale));
        }

        scene.attachChild(number, 0);
        scene.attachChild(overlay, 0);
        scene.attachChild(circle, 0);
        scene.attachChild(approachCircle);
    }

    private void playSound() {
        Utils.playHitSound(listener, soundId, sampleSet, addition);
    }

    private void removeFromScene() {
        if (scene == null) {
            return;
        }

        overlay.clearEntityModifiers();
        circle.clearEntityModifiers();
        number.clearEntityModifiers();
        approachCircle.clearEntityModifiers();

        // Detach all objects
        overlay.detachSelf();
        circle.detachSelf();
        number.detachSelf();
        approachCircle.detachSelf();
        listener.removeObject(this);
        GameObjectPool.getInstance().putCircle(this);
        GameObjectPool.getInstance().putNumber(number);
        scene = null;
    }

    private boolean isHit() {
        for (int i = 0, count = listener.getCursorsCount(); i < count; i++) {

            var inPosition = Utils.squaredDistance(pos, listener.getMousePos(i)) <= radius;
            if (GameHelper.isRelaxMod() && passedTime - time >= 0 && inPosition) {
                return true;
            }

            var isPressed = listener.isMousePressed(this, i);
            if (isPressed && inPosition) {
                return true;
            } else if (GameHelper.isAutopilotMod() && isPressed) {
                return true;
            }
        }
        return false;
    }

    private double hitOffsetToPreviousFrame() {
        // 因为这里是阻塞队列, 所以提前点的地方会影响判断
        for (int i = 0, count = listener.getCursorsCount(); i < count; i++) {

            var inPosition = Utils.squaredDistance(pos, listener.getMousePos(i)) <= radius;
            if (GameHelper.isRelaxMod() && passedTime - time >= 0 && inPosition) {
                return 0;
            }

            var isPressed = listener.isMousePressed(this, i);
            if (isPressed && inPosition) {
                return listener.downFrameOffset(i);
            } else if (GameHelper.isAutopilotMod() && isPressed) {
                return 0;
            }
        }
        return 0;
    }


    @Override
    public void update(final float dt) {
        // PassedTime < 0 means circle logic is over
        if (passedTime < 0) {
            return;
        }
        // If we have clicked circle
        if (replayObjectData != null) {
            if (passedTime - time + dt / 2 > replayObjectData.accuracy / 1000f) {
                final float acc = Math.abs(replayObjectData.accuracy / 1000f);
                if (acc <= GameHelper.getDifficultyHelper().hitWindowFor50(GameHelper.getDifficulty())) {
                    playSound();
                }
                listener.registerAccuracy(replayObjectData.accuracy / 1000f);
                passedTime = -1;
                // Remove circle and register hit in update thread
                listener.onCircleHit(id, replayObjectData.accuracy / 1000f, pos,endsCombo, replayObjectData.result, color);
                removeFromScene();
                return;
            }
        } else if (passedTime * 2 > time && isHit()) {
            float signAcc = passedTime - time;
            if (Config.isFixFrameOffset()) {
                signAcc += (float) hitOffsetToPreviousFrame() / 1000f;
            }
            final float acc = Math.abs(signAcc);
            //Log.i("note-ini", "signAcc: " + signAcc);
            if (acc <= GameHelper.getDifficultyHelper().hitWindowFor50(GameHelper.getDifficulty())) {
                playSound();
            }
            listener.registerAccuracy(signAcc);
            passedTime = -1;
            // Remove circle and register hit in update thread
            float finalSignAcc = signAcc;
            startHit = true;
            listener.onCircleHit(id, finalSignAcc, pos, endsCombo, (byte) 0, color);
            removeFromScene();
            return;
        }

        if (GameHelper.isKiai()) {
            final float kiaiModifier = (float) Math.max(0, 1 - GameHelper.getGlobalTime() / GameHelper.getKiaiTickLength()) * 0.50f;
            final float r = Math.min(1, color.r() + (1 - color.r()) * kiaiModifier);
            final float g = Math.min(1, color.g() + (1 - color.g()) * kiaiModifier);
            final float b = Math.min(1, color.b() + (1 - color.b()) * kiaiModifier);
            kiai = true;
            circle.setColor(r, g, b);
        } else if (kiai) {
            circle.setColor(color.r(), color.g(), color.b());
            kiai = false;
        }

        passedTime += dt;

        // We are still at approach time. Let entity modifiers finish first.
        if (passedTime < time) {
            return;
        }

        if (autoPlay) {
            playSound();
            passedTime = -1;
            // Remove circle and register hit in update thread
            listener.onCircleHit(id, 0, pos, endsCombo, ResultType.HIT300.getId(), color);
            removeFromScene();
        } else {
            approachCircle.clearEntityModifiers();
            approachCircle.setAlpha(0);

            // If passed too much time, counting it as miss
            if (passedTime > time + GameHelper.getDifficultyHelper().hitWindowFor50(GameHelper.getDifficulty())) {
                passedTime = -1;
                final byte forcedScore = (replayObjectData == null) ? 0 : replayObjectData.result;

                removeFromScene();
                listener.onCircleHit(id, 10, pos, false, forcedScore, color);
            }
        }
    } // update(float dt)

    @Override
    public void tryHit(final float dt){
        if (passedTime * 2 > time && isHit()) {
            float signAcc = passedTime - time;
            if (Config.isFixFrameOffset()) {
                signAcc += (float) hitOffsetToPreviousFrame() / 1000f;
            }
            final float acc = Math.abs(signAcc);
            //Log.i("note-ini", "signAcc: " + signAcc);
            if (acc <= GameHelper.getDifficultyHelper().hitWindowFor50(GameHelper.getDifficulty())) {
                playSound();
            }
            listener.registerAccuracy(signAcc);
            passedTime = -1;
            // Remove circle and register hit in update thread
            float finalSignAcc = signAcc;
            listener.onCircleHit(id, finalSignAcc, pos, endsCombo, (byte) 0, color);
            removeFromScene();
        }
    }
}
