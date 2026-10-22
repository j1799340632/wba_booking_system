import {getTicket} from "@/config/request/api/sendNotificationApi.js";

class SseManager {
    constructor() {
        this.eventSource = null;
        // All pages subscribe callback: { eventName: Set([callback1, callback2]) }
        this.listeners = new Map();
        this.reconnectTimer = null;
        this.url = '';
        this.userId = '';
        this.ticket = '';
        // The current connection is a Promise instance.
        this.connectionPromise = null;
        // Record the number of consecutive failures to perform exponential backoff (avoid bombarding the backend)
        this.reconnectAttempts = 0;
    }

    /**
     * Init connection
     */
    connect(userId) {
        // If the currently connected user remains the same, the connection can be reused directly without establishing a new physical channel
        if (this.eventSource && this.userId === userId) {
            if (this.connectionPromise) return this.connectionPromise;
            return Promise.resolve();
        }
        // If a different user logs in, disconnect the old connection first
        this.disconnect();
        this.userId = userId;

        return getTicket().then(data => {
            this.ticket = data.data
            this.url = `${import.meta.env.VITE_SSE_BASE_URL}/general/sse/subscribe/${this.userId}/${this.ticket}`;
            this.connectionPromise = new Promise((resolve, reject) => {
                this._establishConnection(resolve, reject);
            });

            return this.connectionPromise;
        }).catch(err => {
            console.error("Get SSE ticket failed, not permit connect");
            return Promise.reject(err);
        })
    }

    _establishConnection(resolve = null, reject = null) {
        console.log(`[SSE] connecting... user: ${this.userId}, retry count: ${this.reconnectAttempts}`);

        // Close the old listener
        if (this.eventSource) {
            this.eventSource.close();
        }

        this.eventSource = new EventSource(this.url);

        // Listen for physical channel readiness
        this.eventSource.addEventListener('connect', (e) => {
            console.log(e.data)

            // Reset the failed counter
            this.reconnectAttempts = 0;
            if (this.reconnectTimer) {
                clearTimeout(this.reconnectTimer)
            }

            // External promise
            if (resolve) {
                resolve();
                this.connectionPromise = null;
            }
        });

        // Listen for physical disconnections and reconnect
        this.eventSource.onerror = (error) => {
            console.error('SSE disconnected because an error');

            // If the connection fails for the first time, directly let the external .catch() report an error
            if (reject && this.reconnectAttempts === 0) {
                reject(error);
                this.connectionPromise = null;
            }

            // Reconnect
            this._handleDelayReconnect();
        };

        // Bind listeners
        this.listeners.forEach((_, eventName) => this._bindEventToSource(eventName));
    }

    _handleDelayReconnect() {
        if (this.reconnectTimer) {
            clearTimeout(this.reconnectTimer)
        }

        if (!this.userId) {
            return
        }

        this.reconnectAttempts++;

        // The first time it waited 2 seconds, the second time it waited 4 seconds, the third time it waited 8 seconds... The longest it got stuck was 30 seconds before reconnecting
        const delay = Math.min(Math.pow(2, this.reconnectAttempts) * 1000, 30000);

        console.warn(`SSE will attempt reconnecting in ${delay / 1000} seconds, attempt count: ${this.reconnectAttempts}`);

        this.reconnectTimer = setTimeout(() => {
            getTicket()
                .then(data => {
                    this.ticket = data.data;
                    this.url = `${import.meta.env.VITE_SSE_BASE_URL}/general/sse/subscribe/${this.userId}/${this.ticket}`;
                    this._establishConnection(null, null);
                })
                .catch(e => {
                    console.error("Get SSE ticket failed, waiting the next round", e);
                    this._handleDelayReconnect();
                });
        }, delay);
    }

    /**
     * Disconnect safely
     */
    disconnect() {
        if (this.reconnectTimer) {
            clearTimeout(this.reconnectTimer)
        }
        if (this.eventSource) {
            this.eventSource.close();
            this.eventSource = null;
            console.log('SSE disconnected safely');
        }
        this.connectionPromise = null;
        this.reconnectAttempts = 0;
    }

    /**
     * Subscribe to events (for use by various React pages/components)
     * @param {string} eventName same as backend emitter.send().name("xxx")
     * @param {function} callback Callback function after receiving the message
     */
    subscribe(eventName, callback) {
        if (!this.listeners.has(eventName)) {
            this.listeners.set(eventName, new Set());
            this._bindEventToSource(eventName);
        }
        this.listeners.get(eventName).add(callback);

        // Returns an unsubscribe function for useEffect to gracefully destroy.
        return () => this.unsubscribe(eventName, callback);
    }

    /**
     * Unsubscribe from an event
     */
    unsubscribe(eventName, callback) {
        const callbacks = this.listeners.get(eventName);
        if (callbacks) {
            callbacks.delete(callback);
            // If this event has no more page subscriptions, then unbind it from the underlying physical channel
            if (callbacks.size === 0) {
                this.listeners.delete(eventName);
                if (this.eventSource) {
                    this.eventSource.removeEventListener(eventName, this._handleMessage);
                }
            }
        }
    }

    /**
     * The core of binding event listeners
     */
    _bindEventToSource(eventName) {
        if (!this.eventSource) {
            return
        }

        // Remove first to prevent duplicate binding
        this.eventSource.removeEventListener(eventName, this._handleMessage);

        // Dynamically bind event listeners
        this.eventSource.addEventListener(eventName, event => {
            this._handleMessage(event)
        });
    }

    /**
     * Unified message forwarding and dispatching
     */
    _handleMessage = (event) => {
        const eventName = event.type;
        const callbacks = this.listeners.get(eventName);
        if (callbacks) {
            // Notify all React component pages that have subscribed to the event
            callbacks.forEach(cb => cb(event.data));
        }
    }
}

const sseManager = new SseManager();
export default sseManager;
