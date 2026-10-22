import './index.css';
import {useOutlet} from "react-router-dom";
import {AnimatePresence, motion} from "framer-motion";
import Aside from "@/pages/Layout/Aside/index.jsx";
import {useEffect} from "react";
import sseManager from "@/utils/sse/sseManager.js";
import {useUserStore} from "@/config/store/useUserStore.js";
import {notify} from "@/utils/notify.js";
import {notificationEvent} from "@/utils/sse/sseEventChannel.js";
import {Header} from "@/pages/Layout/Header/index.jsx";

export default function Home() {
  const user = useUserStore((state) => state.userInfo);
  const userId = user?.id;

  const globalSseReceiver = (data) => {
    // console.log('%c[Global SSE receive a message]', 'color: #0070f3; font-weight: bold; background: #e6f7ff; padding: 2px 6px; border-radius: 3px;');
    // console.log('Content:', data);
    notify.info('System Notify: ' + data)
  };

  useEffect(() => {
    if (!userId) {
      return
    }
    sseManager.connect(userId)
      .then(() => {
        sseManager.subscribe(notificationEvent, globalSseReceiver);
      })
  }, [userId]);

  const currentOutlet = useOutlet();

  return (
    <>
      <Aside />

      <div className={'home-container'} id={'home-container'}>
        <Header />
          <AnimatePresence mode="wait">
            <motion.div
              className={'home-div'}
              key={location.pathname}
              initial={{ opacity: 0, y: 30 }}
              // In
              animate={{
                opacity: 1,
                y: 0,
                transition: {
                  type: "spring",
                  // Rigidity determines rebound speed
                  stiffness: 300,
                  // Damping determines the number of rebounds.
                  damping: 24
                }
              }}
              // Out
              exit={{
                opacity: 0,
                y: -15,
                // Do not use spring type, it will slow the next animation start
                transition: {
                  duration: 0.15,
                  ease: "easeIn"
                }
            }}
            >
              {currentOutlet}
            </motion.div>
          </AnimatePresence>
      </div>
    </>
  );
}
