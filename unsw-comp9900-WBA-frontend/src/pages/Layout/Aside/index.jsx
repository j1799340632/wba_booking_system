import "./index.css";
import { useNavigate, useLocation } from "react-router-dom";
import { useUserStore } from "@/config/store/useUserStore.js";
import {Settings, LogOut, X, User} from "lucide-react";
import { notify } from "@/utils/notify.js";
import {COMMON_ROUTES, ROUTE_MAP} from "@/config/router/routeMap.jsx";
import { generateMenu } from "@/utils/routeUtils.js";
import { logout } from "@/config/request/api/userApi.js";
import sseManager from "@/utils/sse/sseManager.js";
import { useAvailabilityStore } from "@/config/store/useAvailabilityStore.js";
import {applyDisconnect} from "@/config/request/api/sendNotificationApi.js";
import {useSidebarStore} from "@/config/store/sidebarStore.js";

export default function Aside() {
  const {isOpenAside, setIsOpenAside} = useSidebarStore();

  const {userInfo, clearUserInfo} = useUserStore();

  const userName = userInfo?.name || "User";

  const avatarSrc = userInfo?.avatar
  ? `${userInfo.avatar}${
      userInfo.avatar.includes("?") ? "&" : "?"
    }v=${userInfo.avatarUpdatedAt || 0}`
  : "";

  const roleName = userInfo?.roleName || "Student";
  const navigate = useNavigate();
  const location = useLocation();

  const userRole = String(userInfo?.roleId || "3");
  const navItems = generateMenu([...COMMON_ROUTES, ...ROUTE_MAP[userRole]]);

  const isSupervisor = userRole === "2";
  const isAvailable = useAvailabilityStore((state) => state.isAvailable);

  const handleNavigate = (path) => {
    navigate(path);
    setIsOpenAside(false);
  };

  const signOut = () => {
    logout().then(() => {
      navigate("/", {replace: true});
      notify.success("Sign out successfully");
      setIsOpenAside(false);

      // Shutdown SSE connection
      sseManager.disconnect()
      void applyDisconnect(userInfo.id)

      setTimeout(() => {
        clearUserInfo()
      }, 1000);
    });
  };

  return (
    <>
      {!!isOpenAside && <div className="sidebar-overlay" onClick={() => setIsOpenAside(false)} />}

      <aside className={`sidebar ${isOpenAside ? "open" : ""}`}>
          {!!isOpenAside && (
            <button className="sidebar-close-button" onClick={() => setIsOpenAside(false)}>
              <X size={22} />
            </button>
          )}

            <div className="sidebar-logo">
              <img
                src="/unsw-shield.svg"
                alt="UNSW Shield"
                className="sidebar-logo-image"
              />

              <div className="sidebar-logo-text">
                <h2 className="sidebar-title">WBA Platform</h2>
                <p className="sidebar-subtitle">UNSW Medicine</p>
              </div>
            </div>

        <div className="sidebar-user">
          <div className="sidebar-user-info">
          <div className="sidebar-avatar">
                {avatarSrc ? (
                  <img
                    src={avatarSrc}
                    alt={userName}
                    className="sidebar-avatar-image"
                  />
                ) : (
                  <User size={18} style={{color: '#4b5563'}}/>
                )}

                {isSupervisor && (
                  <span
                    className={`sidebar-avatar-status ${
                      isAvailable ? "is-online" : "is-offline"
                    }`}
                    title={isAvailable ? "Available" : "Unavailable"}
                  />
                )}
              </div>

            <div className="sidebar-user-text">
              <div className="sidebar-name">{userName}</div>
              <div className="sidebar-role">{roleName}</div>
            </div>
          </div>
        </div>

        <nav className="sidebar-nav">
          {navItems.map((item) => {
            const Icon = item.handle.icon;
            const active = location.pathname === item.path;

            return (
              <button
                key={item.path}
                onClick={() => handleNavigate(item.path)}
                className={`sidebar-nav-button ${active ? "active" : ""}`}
              >
                <Icon size={18} />
                <span>{item.handle.title}</span>
              </button>
            );
          })}
        </nav>

        <div className="sidebar-footer">
          <button
            className="sidebar-bottom-button"
            onClick={() => navigate("/home/settings")}
          >
            <Settings size={18} />
            Settings
          </button>

          <button className="sidebar-bottom-button" onClick={signOut}>
            <LogOut size={18} />
            Sign Out
          </button>
        </div>
      </aside>
    </>
  );
}
