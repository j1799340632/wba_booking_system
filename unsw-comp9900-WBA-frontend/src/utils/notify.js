import { toast } from 'sonner';
import successSound from '@/assets/audio/success-notify-sound.mp3';
import errorSound from '@/assets/audio/error-notify-sound.mp3';
import infoSound from '@/assets/audio/info-notify-sound.mp3';
import warningSound from '@/assets/audio/warning-notify-sound.mp3';
import {useSettingsStore} from "@/config/store/useSettingsStore.js";

const audioPool = {
    success: typeof Audio !== 'undefined' ? new Audio(String(successSound)) : null,
    error: typeof Audio !== 'undefined' ? new Audio(String(errorSound)) : null,
    info: typeof Audio !== 'undefined' ? new Audio(String(infoSound)) : null,
    warning: typeof Audio !== 'undefined' ? new Audio(String(warningSound)) : null,
};

Object.values(audioPool).forEach(audio => {
    if (audio) {
        audio.preload = 'auto';
    }
});

const playSound = type => {
    // Get settings
    const isSoundEnabled = useSettingsStore.getState().soundEnabled;
    if (!isSoundEnabled) return;

    const baseAudio = audioPool[type];
    if (!baseAudio) return;

    const audioClone = baseAudio.cloneNode()
    audioClone.volume = 0.5;

    audioClone.play().catch((error) => {
        console.warn('The browser blocked automatic audio playback: ', error);
    });
};

export const notify = {
    success: (message, options) => {
        playSound('success');
        return toast.success(message, options);
    },

    error: (message, options) => {
        playSound('error');
        return toast.error(message, options);
    },

    info: (message, options) => {
        playSound('info');
        return toast.info(message, options);
    },

    warning: (message, options) => {
        playSound('warning');
        return toast.warning(message, options);
    },

    default: (message, options) => {
        playSound('info');
        return toast(message, options);
    }
};
