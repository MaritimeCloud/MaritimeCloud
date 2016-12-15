/* Copyright (c) 2011 Danish Maritime Authority.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.maritimecloud.internal.mms.client.connection;

import static java.util.Objects.requireNonNull;

import java.net.URI;
import java.util.concurrent.CopyOnWriteArraySet;

import net.maritimecloud.internal.util.logging.Logger;
import net.maritimecloud.net.mms.MmsClientConfiguration;
import net.maritimecloud.net.mms.MmsConnection;
import net.maritimecloud.net.mms.MmsConnectionClosingCode;

/**
 *
 * @author Kasper Nielsen
 */
class MmsConnectionListenerInvoker implements MmsConnection.Listener {

    /** The logger. */
    private static final Logger LOGGER = Logger.get(ClientConnection.class);

    /** Listeners for updates to the connection status. */
    final CopyOnWriteArraySet<MmsConnection.Listener> listeners = new CopyOnWriteArraySet<>();

    final ClientConnection clientConnection;

    MmsConnectionListenerInvoker(ClientConnection dcc, MmsClientConfiguration b) {
        this.clientConnection = requireNonNull(dcc);
        for (MmsConnection.Listener listener : b.getListeners()) {
            listeners.add(requireNonNull(listener));
        }
    }

    /** {@inheritDoc} */
    @Override
    public void binaryMessageReceived(byte[] message) {
        for (MmsConnection.Listener l : listeners) {
            try {
                l.binaryMessageReceived(message);
            } catch (Exception e) {
                LOGGER.error("Failure", e);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void binaryMessageSend(byte[] message) {
        for (MmsConnection.Listener l : listeners) {
            try {
                l.binaryMessageSend(message);
            } catch (Exception e) {
                LOGGER.error("Failure", e);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void connected(URI host, boolean sessionId) {
        clientConnection.disconnectedOrConnected();
        for (MmsConnection.Listener l : listeners) {
            try {
                l.connected(host, sessionId);
            } catch (Exception e) {
                LOGGER.error("Failure", e);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void connecting(URI host) {
        for (MmsConnection.Listener l : listeners) {
            try {
                l.connecting(host);
            } catch (Exception e) {
                LOGGER.error("Failure", e);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void disconnected(MmsConnectionClosingCode closeReason) {
        clientConnection.disconnectedOrConnected();
        for (MmsConnection.Listener l : listeners) {
            try {
                l.disconnected(closeReason);
            } catch (Exception e) {
                LOGGER.error("Failure", e);
            }

        }
    }

    /** {@inheritDoc} */
    @Override
    public void textMessageReceived(String message) {
        for (MmsConnection.Listener l : listeners) {
            try {
                l.textMessageReceived(message);
            } catch (Exception e) {
                LOGGER.error("Failure", e);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void textMessageSend(String message) {
        for (MmsConnection.Listener l : listeners) {
            try {
                l.textMessageSend(message);
            } catch (Exception e) {
                LOGGER.error("Failure", e);
            }
        }
    }
}
