import { create } from 'zustand';

export const useConfirmStore = create((set) => ({
  isOpen: false,
  title: '',
  content: '',
  onConfirm: () => { /* empty */ },
  showConfirm: (title = 'Reminder', content = 'Are you sure to confirm?', onConfirm) => set({ isOpen: true, title, content, onConfirm }),
  closeConfirm: () => set({ isOpen: false }),
}));
