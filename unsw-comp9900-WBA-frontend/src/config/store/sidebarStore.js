import {create} from 'zustand';

export const useSidebarStore = create(set => ({
    isOpenAside: false,
    setIsOpenAside: isOpen => set({isOpenAside: isOpen}),
    toggleSidebar: () => set(state => ({isOpenAside: !state.isOpenAside})),
}));
