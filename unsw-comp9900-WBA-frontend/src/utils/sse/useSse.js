import { useEffect, useCallback } from 'react';
import sseManager from './sseManager.js';

/**
 * React components Hook
 * @param {string} eventName subscribe event name
 * @param {function} callback callback function when receiving a message
 */
export function useSse(eventName, callback) {

    // Use `useCallback` to persist an anonymous function passed in from outside. Its reference will only change when the content of the callback function itself or its dependencies actually change
    const memoizedCallback = useCallback((data) => {
        if (callback) {
            callback(data);
        }
    }, [callback]);

    useEffect(() => {
        if (!eventName) return;

        const unsubscribe = sseManager.subscribe(eventName, memoizedCallback);

        // Unsubscribe when the component is destroyed
        return () => {
            unsubscribe();
        };
    }, [eventName, memoizedCallback]);
}
