import './index.css';
import { motion, AnimatePresence } from 'framer-motion';
import { useConfirmStore } from '@/config/store/useConfirmStore.js';
import { useLocation } from 'react-router-dom';
import {useEffect, useRef} from 'react';

export default function GlobalConfirm() {
  const { isOpen, title, content, onConfirm, closeConfirm } = useConfirmStore();

  // Current route
  const location = useLocation();
  const prevPathname = useRef(location.pathname);

  // If location.pathname has changed (like user clicks the back button in the browser), close the dialog
  useEffect(() => {
    if (prevPathname.current !== location.pathname) {
      closeConfirm();
      prevPathname.current = location.pathname;
    }
  }, [location.pathname, closeConfirm]);

  const handleConfirm = () => {
    onConfirm();
    closeConfirm();
  };

  return (
    <AnimatePresence>
      {isOpen && (
        <motion.div
          className="dialog-confirm-overlay"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.2 }}
          onClick={closeConfirm}
        >
          <motion.div
            initial={{ opacity: 0, scale: 0.95 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.95 }}
            transition={{ type: 'spring', duration: 0.4, bounce: 0.3 }}
            className="dialog-confirm-modal"
            // Stop event bubble
            onClick={(e) => e.stopPropagation()}
          >
            <h2 className="dialog-confirm-title">{title}</h2>
            <p className="dialog-confirm-content">{content}</p>

            <div className="dialog-confirm-actions">
              <button
                onClick={handleConfirm}
                className="dialog-confirm-btn dialog-confirm-btn-ok"
              >
                                Confirm
              </button>
              <button
                onClick={closeConfirm}
                className="dialog-confirm-btn dialog-confirm-btn-cancel"
              >
                    Cancel
              </button>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
