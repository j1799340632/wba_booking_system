import './index.css';
import {useOutlet, useLocation, useSearchParams} from "react-router-dom";
import { AnimatePresence, motion } from "framer-motion";
import {useEffect} from "react";
import {notify} from "@/utils/notify.js";

export default function Welcome() {
  const currentOutlet = useOutlet();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();

  useEffect(() => {
    if (searchParams.get('alert') === 'Please_login_first') {
      notify.warning('Please login first');
      searchParams.delete('alert');
      setSearchParams(searchParams, { replace: true });
    }
  }, [searchParams, setSearchParams]);

  return (
    <>
      <AnimatePresence mode="wait">
        <motion.div
          className={'welcome-section-inputs'}
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
    </>
  );
}
