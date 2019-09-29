/*
 * Copyright (C) 2013 The Android Open Source Project
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

package android.media;

import android.content.Intent;
import android.media.IMediaRouter2Client;
import android.media.IMediaRouter2Manager;
import android.media.IMediaRouterClient;
import android.media.MediaRoute2Info;
import android.media.MediaRouterClientState;

/**
 * {@hide}
 */
interface IMediaRouterService {
    //TODO: Merge or remove methods when media router 2 is done.
    void registerClientAsUser(IMediaRouterClient client, String packageName, int userId);
    void unregisterClient(IMediaRouterClient client);

    void registerClientGroupId(IMediaRouterClient client, String groupId);

    MediaRouterClientState getState(IMediaRouterClient client);
    boolean isPlaybackActive(IMediaRouterClient client);

    void setDiscoveryRequest(IMediaRouterClient client, int routeTypes, boolean activeScan);
    void setSelectedRoute(IMediaRouterClient client, String routeId, boolean explicit);
    void requestSetVolume(IMediaRouterClient client, String routeId, int volume);
    void requestUpdateVolume(IMediaRouterClient client, String routeId, int direction);

    // Methods for media router 2
    void registerClient2AsUser(IMediaRouter2Client client, String packageName, int userId);
    void unregisterClient2(IMediaRouter2Client client);
    void sendControlRequest(IMediaRouter2Client client, in MediaRoute2Info route, in Intent request);
    /**
     * Changes the selected route of the client.
     *
     * @param client the client that changes it's selected route
     * @param route the route to be selected
     */
    void selectRoute2(IMediaRouter2Client client, in @nullable MediaRoute2Info route);
    void setControlCategories(IMediaRouter2Client client, in List<String> categories);

    void registerManagerAsUser(IMediaRouter2Manager manager,
            String packageName, int userId);
    void unregisterManager(IMediaRouter2Manager manager);
    /**
     * Changes the selected route of an application.
     *
     * @param manager the manager that calls the method
     * @param packageName the package name of the client that will change the selected route
     * @param route the route to be selected
     */
    void selectClientRoute2(IMediaRouter2Manager manager, String packageName,
            in @nullable MediaRoute2Info route);
}
