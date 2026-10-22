import {create} from 'zustand';
import {persist, createJSONStorage} from 'zustand/middleware';

export const useSettingsStore = create()(
  persist(
    (set) => ({
      soundEnabled: true,
      emailNotificationEnabled: true,
      setSoundEnabled: (value) => set({soundEnabled: value}),
      setEmailNotificationEnabled: (value) =>
        set({emailNotificationEnabled: value}),
    }),
    {
      name: 'app-settings-storage',
      storage: createJSONStorage(() => localStorage),
    }
  )
);