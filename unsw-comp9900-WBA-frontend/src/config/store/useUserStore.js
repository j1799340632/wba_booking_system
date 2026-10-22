import {create} from 'zustand';
import {persist, createJSONStorage} from 'zustand/middleware';

/**
 * @typedef {Object} UserState
 * @property {any} userInfo
 * @property {function(any): void} setUserInfo
 * @property {function(): void} clearUserInfo
 */

/** @type {import('zustand').UseBoundStore<import('zustand').StoreApi<UserState>>} */
export const useUserStore = create()(
  persist(
    (set) => ({
      userInfo: null,
      setUserInfo: user => set({userInfo: user}),
      clearUserInfo: () => set({userInfo: null}),
    }),
    {
      name: 'user-session-storage',
      // Zustand default use localStorage，we choose sessionStorage
      storage: createJSONStorage(() => sessionStorage),
    }
  )
);
