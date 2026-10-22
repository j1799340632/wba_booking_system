import { create } from "zustand";

/**
 * @typedef {Object} AvailabilityState
 * @property {boolean} isAvailable
 * @property {function(boolean): void} setAvailable
 */

/** @type {import('zustand').UseBoundStore<import('zustand').StoreApi<AvailabilityState>>} */
export const useAvailabilityStore = create((set) => ({
  isAvailable: false,
  setAvailable: (value) => set({ isAvailable: value }),
}));