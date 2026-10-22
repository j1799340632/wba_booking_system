import {create} from 'zustand';

// Manipulate DOM nodes to trigger CSS inversion
const applyThemeToDOM = (isDark) => {
    if (typeof document === 'undefined') return;
    const root = document.documentElement;
    if (isDark) {
        root.setAttribute('data-theme', 'dark');
    } else {
        root.removeAttribute('data-theme');
    }
};

export const useThemeStore = create((set, get) => {
    // Initialization check: First check if there is any manual overwriting (localStorage); if not, use the system's matchMedia
    const getInitialDarkMode = () => {
        if (typeof window === 'undefined') {
            return false;
        }

        const savedTheme = localStorage.getItem('app-theme');
        if (savedTheme === 'dark') {
            return true;
        }
        if (savedTheme === 'light') {
            return false;
        }

        // Detection system topic
        return window.matchMedia('(prefers-color-scheme: dark)').matches;
    };

    const initialDark = getInitialDarkMode();
    applyThemeToDOM(initialDark);

    return {
        isDarkMode: initialDark,

        // Manually switch themes
        toggleTheme: () => {
            const nextDark = !get().isDarkMode;
            applyThemeToDOM(nextDark);

            // Record user's manual selection
            localStorage.setItem('app-theme', nextDark ? 'dark' : 'light');

            set({isDarkMode: nextDark});
        },

        // Set/Update Theme
        setDarkMode: (isDark) => {
            applyThemeToDOM(isDark);
            set({isDarkMode: isDark});
        },

        // Reset to follow system mode
        resetToSystemTheme: () => {
            localStorage.removeItem('app-theme');
            const systemDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
            applyThemeToDOM(systemDark);
            set({isDarkMode: systemDark});
        }
    };
});

// Register a global listener for system theme changes (automatically updates when the OS theme changes)
if (typeof window !== 'undefined') {
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');

    mediaQuery.addEventListener('change', (e) => {
        const savedTheme = localStorage.getItem('app-theme');
        // Only follow system changes if the user does not explicitly and manually override the theme
        if (!savedTheme) {
            useThemeStore.getState().setDarkMode(e.matches);
        }
    });
}
