import './index.css';
import {Fragment, useRef, useState} from 'react';
import {useNavigate, useMatches, Link} from 'react-router-dom';
import {
    ArrowLeft,
    Sun,
    Moon,
    Settings,
    Menu,
    X,
    ChevronRight,
    User
} from 'lucide-react';
import {motion, AnimatePresence} from 'framer-motion';
import {useSidebarStore} from "@/config/store/sidebarStore.js";
import {useUserStore} from "@/config/store/useUserStore.js";
import NotificationBell from "@/components/NotificationBell/index.jsx";
import {useThemeStore} from "@/config/store/useThemeStore.js";

export const Header = () => {
    const navigate = useNavigate();
    const matches = useMatches();
    const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);

    // Toggle sidebar
    const {toggleSidebar} = useSidebarStore();
    // Toggle Light/Dark theme
    const {isDarkMode, toggleTheme} = useThemeStore();
    const {userInfo} = useUserStore();

    // Message center button ref
    const bellRef = useRef(null);

    const breadcrumbs = matches
        .filter(match => Boolean(match.handle?.crumb)).map(match => ({
            path: match.pathname,
            label: match.handle.crumb,
        }));

    return (
        <header className="app-header">
            {/* Left */}
            <div className="header-left">
                {/* Back button */}
                <button
                    className="header-back-btn"
                    onClick={() => navigate(-1)}
                    aria-label="Back"
                >
                    <ArrowLeft size={18}/>
                    <span>Back</span>
                </button>

                {/* When screen size < 768px, display avatar + Sidebar button */}
                <button
                    className="header-sidebar-trigger"
                    onClick={() => toggleSidebar()}
                    aria-label="Open Sidebar"
                >
                    <div className="header-avatar-box">
                        {userInfo.avatar ? (
                            <img src={userInfo.avatar} alt="Avatar" className="header-avatar-img"/>
                        ) : (
                            <User size={18} style={{color: '#4b5563'}}/>
                        )}
                    </div>
                    <span className="header-sidebar-text">Sidebar</span>
                </button>

                {/* Breadcrumbs */}
                <nav className="header-breadcrumbs">
                    {breadcrumbs.map((item, index) => {
                        const isLast = index === breadcrumbs.length - 1;
                        return (
                            <Fragment key={item.path}>
                                {index > 0 && (
                                    <ChevronRight size={14} className="header-breadcrumb-separator"/>
                                )}
                                {isLast ? (
                                    <span className="header-breadcrumb-active">{item.label}</span>
                                ) : (
                                    <Link to={item.path} className="header-breadcrumb-link">
                                        {item.label}
                                    </Link>
                                )}
                            </Fragment>
                        );
                    })}
                </nav>
            </div>

            {/* Right */}
            <div className="header-right">
                <div className="header-desktop-actions">
                    <button
                        onClick={toggleTheme}
                        className="header-icon-btn"
                        title="Toggle Theme"
                    >
                        {isDarkMode ? <Sun size={20}/> : <Moon size={20}/>}
                    </button>

                    <NotificationBell ref={bellRef}/>

                    <button
                        className="header-icon-btn"
                        title="Settings"
                        onClick={() => navigate("/home/settings")}
                    >
                        <Settings size={20}/>
                    </button>
                </div>

                {/* Mobile foldable menu button */}
                <button
                    className="header-mobile-toggle"
                    onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
                    aria-label="Toggle Menu"
                >
                    {isMobileMenuOpen ? <X size={22}/> : <Menu size={22}/>}
                </button>
            </div>

            {/* Mobile Foldable Menu */}
            <AnimatePresence>
                {isMobileMenuOpen && (
                    <>
                        {/* Overlay */}
                        <motion.div
                            initial={{opacity: 0}}
                            animate={{opacity: 1}}
                            exit={{opacity: 0}}
                            transition={{duration: 0.15}}
                            className="header-mobile-overlay"
                            onClick={() => setIsMobileMenuOpen(false)}
                        />
                        <motion.div
                            initial={{opacity: 0, height: 0}}
                            animate={{opacity: 1, height: 'auto'}}
                            exit={{opacity: 0, height: 0}}
                            transition={{duration: 0.2}}
                            className="header-mobile-menu"
                        >
                            {breadcrumbs.length > 0 && (
                                <div className="header-mobile-breadcrumb-box">
                                    <span className="header-mobile-breadcrumb-title">Current</span>
                                    <div className="header-mobile-breadcrumb-list">
                                        {breadcrumbs.map((item, index) => {
                                            const isLast = index === breadcrumbs.length - 1;
                                            return (
                                                <span
                                                    key={item.path}
                                                    className={`header-mobile-breadcrumb-item ${isLast ? 'active' : ''}`}
                                                >
                                                {item.label} {!isLast && '/'}
                                            </span>
                                            );
                                        })}
                                    </div>
                                </div>
                            )}

                            <div className="header-mobile-action-list">
                                <button
                                    onClick={() => {
                                        toggleTheme();
                                        setIsMobileMenuOpen(false);
                                    }}
                                    className="header-mobile-menu-item"
                                >
                                    {isDarkMode ? <Sun size={18}/> : <Moon size={18}/>}
                                    <span style={{marginLeft: '10px'}}>
                                    {isDarkMode ? 'Light' : 'Dark'}
                                </span>
                                </button>

                                <div
                                    onClick={() => {
                                        bellRef.current?.open();
                                    }}
                                    className="header-mobile-menu-item"
                                >
                                    <NotificationBell ref={bellRef}/>
                                    <span style={{marginLeft: '10px'}}>Notification</span>
                                </div>

                                <button
                                    onClick={() => {
                                        navigate("/home/settings");
                                        setIsMobileMenuOpen(false);
                                    }}
                                    className="header-mobile-menu-item"
                                >
                                    <Settings size={18}/>
                                    <span style={{marginLeft: '10px'}}>Settings</span>
                                </button>
                            </div>
                        </motion.div>
                    </>
                )}
            </AnimatePresence>
        </header>
    );
};
