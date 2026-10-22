import './App.css';
import { useOutlet, useMatches } from 'react-router-dom';
import { AnimatePresence, motion } from "framer-motion";
import { useRouteTitle } from '@/config/router/useRouteTitle';
import { Toaster } from 'sonner';
import GlobalConfirm from "@/components/GlobalConfirm/index.jsx";

function App() {
  const currentOutlet = useOutlet();
  useRouteTitle('WBA-Platform');
  const matches = useMatches();
  // This ID will never change as long as you stay on this page
  const layoutKey = matches.length > 1 ? matches[1].id : location.pathname;

  return (
      <>
        <Toaster richColors position="top-right" expand={false} closeButton />
        <GlobalConfirm />

        <AnimatePresence mode="wait">
          <motion.main
              className="main-content"
              // If key changes, the animation will start again
              key={layoutKey}
              // Initial
              initial={{ opacity: 0, y: 15 }}
              // In
              animate={{ opacity: 1, y: 0 }}
              // Out
              exit={{ opacity: 0, y: -15 }}
              transition={{ duration: 0.3, ease: "easeOut" }}
          >
            {currentOutlet}
          </motion.main>
        </AnimatePresence>
      </>
  )
}

export default App
