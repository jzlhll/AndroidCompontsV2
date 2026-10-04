package childmonitor.platform;

import android.annotation.SuppressLint;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.impl.Observable;
import androidx.camera.video.EncoderProfilesResolver;
import androidx.camera.video.MediaSpec;
import androidx.camera.video.Recorder;
import androidx.camera.video.VideoCapabilities;
import androidx.camera.video.VideoOutput;

/** 显式转发 Recorder 的录像配置，通过 Java 避免 Kotlin 引用 CameraX 的 internal 类型。 */
@SuppressLint("RestrictedApi")
public abstract class RecorderVideoOutput implements VideoOutput {
    private final Recorder specification;

    protected RecorderVideoOutput(Recorder specification) {
        this.specification = specification;
    }

    @Override
    public Observable<MediaSpec> getMediaSpec() {
        return specification.getMediaSpec();
    }

    @Override
    public VideoCapabilities getMediaCapabilities(CameraInfo cameraInfo, int sessionType) {
        return specification.getMediaCapabilities(cameraInfo, sessionType);
    }

    @Override
    public EncoderProfilesResolver getEncoderProfilesResolver(CameraInfo cameraInfo, int sessionType) {
        return specification.getEncoderProfilesResolver(cameraInfo, sessionType);
    }

    @Override
    public boolean isQualitySelectorDefault() {
        return specification.isQualitySelectorDefault();
    }
}
