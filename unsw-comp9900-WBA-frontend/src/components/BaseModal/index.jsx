import './index.css';
import { useEffect } from 'react';
import { createPortal } from 'react-dom';
import { motion, AnimatePresence } from 'framer-motion';

export default function BaseModal({
  isOpen,
  onClose,
  title,
  children,
  width = '500px'
}) {

  // Disable scrolling of the underlying body when the pop-up window opens
  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = 'unset';
    }
    return () => {
      document.body.style.overflow = 'unset';
    };
  }, [isOpen]);

  // If it is off and no animation is running, nothing is rendered.
  if (!isOpen && typeof document === 'undefined') return null;

  // Mount to document.body
  return createPortal(
    <AnimatePresence>
      {isOpen && (
        <motion.div
          className="base-modal-overlay"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.2 }}
          // Click mask can close the modal
          //onClick={onClose}
        >
          <motion.div
            className="base-modal-container"
            style={{ width, maxWidth: '95vw' }}
            initial={{ opacity: 0, scale: 0.95, y: 10 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.95, y: 10 }}
            transition={{ type: 'spring', duration: 0.4, bounce: 0.3 }}
            // Prevent click event bubbling; otherwise, clicking inside the pop-up window will also trigger the close event if you open 'Click mask can close the modal'
            onClick={(e) => e.stopPropagation()}
          >
            {/* Header region */}
            <div className="base-modal-header">
              <h2 className="base-modal-title">{title}</h2>
              <button className="base-modal-close-btn" onClick={onClose} aria-label="Close">
                                &times; {/* Close icon */}
              </button>
            </div>

            {/* Content region */}
            <div className="base-modal-body">
              {children}
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>,
    document.body
  );
}
