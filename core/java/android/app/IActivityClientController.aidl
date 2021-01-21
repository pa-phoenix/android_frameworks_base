/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.app;

import android.app.ActivityManager;
import android.app.PictureInPictureParams;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.view.RemoteAnimationDefinition;

/**
 * Interface for the callback and request from an activity to system.
 *
 * {@hide}
 */
interface IActivityClientController {
    oneway void activityIdle(in IBinder token, in Configuration config, in boolean stopProfiling);
    void activityResumed(in IBinder token);
    void activityTopResumedStateLost();
    void activityPaused(in IBinder token);
    void activityStopped(in IBinder token, in Bundle state, in PersistableBundle persistentState,
            in CharSequence description);
    oneway void activityDestroyed(in IBinder token);
    void activityRelaunched(in IBinder token);

    void reportSizeConfigurations(in IBinder token, in int[] horizontalSizeConfiguration,
            in int[] verticalSizeConfigurations, in int[] smallestWidthConfigurations);
    boolean moveActivityTaskToBack(in IBinder token, boolean nonRoot);
    boolean shouldUpRecreateTask(in IBinder token, in String destAffinity);
    boolean navigateUpTo(in IBinder token, in Intent target, int resultCode,
            in Intent resultData);
    boolean releaseActivityInstance(in IBinder token);
    boolean finishActivity(in IBinder token, int code, in Intent data, int finishTask);
    boolean finishActivityAffinity(in IBinder token);
    /** Finish all activities that were started for result from the specified activity. */
    void finishSubActivity(in IBinder token, in String resultWho, int requestCode);

    boolean isTopOfTask(in IBinder token);
    boolean willActivityBeVisible(in IBinder token);
    int getDisplayId(in IBinder activityToken);
    int getTaskForActivity(in IBinder token, in boolean onlyRoot);
    ComponentName getCallingActivity(in IBinder token);
    String getCallingPackage(in IBinder token);
    int getLaunchedFromUid(in IBinder token);
    String getLaunchedFromPackage(in IBinder token);
    Bundle getActivityOptions(in IBinder token);

    void setRequestedOrientation(in IBinder token, int requestedOrientation);
    int getRequestedOrientation(in IBinder token);

    boolean convertFromTranslucent(in IBinder token);
    boolean convertToTranslucent(in IBinder token, in Bundle options);

    boolean isImmersive(in IBinder token);
    void setImmersive(in IBinder token, boolean immersive);

    boolean enterPictureInPictureMode(in IBinder token, in PictureInPictureParams params);
    void setPictureInPictureParams(in IBinder token, in PictureInPictureParams params);
    void toggleFreeformWindowingMode(in IBinder token);

    void startLockTaskModeByToken(in IBinder token);
    void stopLockTaskModeByToken(in IBinder token);
    oneway void showLockTaskEscapeMessage(in IBinder token);
    void setTaskDescription(in IBinder token, in ActivityManager.TaskDescription values);

    boolean showAssistFromActivity(in IBinder token, in Bundle args);
    boolean isRootVoiceInteraction(in IBinder token);
    void startLocalVoiceInteraction(in IBinder token, in Bundle options);
    void stopLocalVoiceInteraction(in IBinder token);

    void setShowWhenLocked(in IBinder token, boolean showWhenLocked);
    void setInheritShowWhenLocked(in IBinder token, boolean setInheritShownWhenLocked);
    void setTurnScreenOn(in IBinder token, boolean turnScreenOn);
    void reportActivityFullyDrawn(in IBinder token, boolean restoredFromBundle);
    void overridePendingTransition(in IBinder token, in String packageName,
            int enterAnim, int exitAnim);
    int setVrMode(in IBinder token, boolean enabled, in ComponentName packageName);

    /** See {@link android.app.Activity#setDisablePreviewScreenshots}. */
    void setDisablePreviewScreenshots(in IBinder token, boolean disable);

    /** Registers remote animations for a specific activity. */
    void registerRemoteAnimations(in IBinder token, in RemoteAnimationDefinition definition);

    /** Unregisters all remote animations for a specific activity. */
    void unregisterRemoteAnimations(in IBinder token);

    /**
     * Reports that an Activity received a back key press when there were no additional activities
     * on the back stack.
     */
    void onBackPressedOnTaskRoot(in IBinder token);
}
