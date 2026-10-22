import "./index.css";
import {useEffect, useState} from "react";
import {
  Mail,
  User,
  Lock,
  Volume2,
  Bell,
  KeyRound,
} from "lucide-react";
import { useUserStore } from "@/config/store/useUserStore.js";
import { useSettingsStore } from "@/config/store/useSettingsStore.js";
import BaseModal from "@/components/BaseModal/index.jsx";
import {ChangePasswordModal} from "@/components/ChangePasswordModal/index.jsx";
import {getEmailVerificationCode} from "@/config/request/api/verifyApi.js";
import {resetOwnPassword} from "@/config/request/api/userApi.js";
import {getUserSettings, saveUserSettings} from "@/config/request/api/userSettingsApi.js";
import {notify} from "@/utils/notify.js";

export default function Settings() {
  const user = useUserStore((state) => state.userInfo);

  const {soundEnabled, setSoundEnabled} = useSettingsStore();
  const {emailNotificationEnabled, setEmailNotificationEnabled} = useSettingsStore();

  const [modalOpen, setModalOpen] = useState(false);

  useEffect(() => {
    getUserSettings().then(res => {
      const userSettings = res.data;
      setEmailNotificationEnabled(userSettings.isReceiveEmail);
      setSoundEnabled(userSettings.isMessageSound);
    })
  }, [])

  const openModal = () => {
    setModalOpen(true);
  };

  const closeModal = () => {
    setModalOpen(false);
  };

  const getInitial = () => {
    const name = user?.name || user?.email || "?";
    return String(name).trim().charAt(0).toUpperCase();
  };

  const changeSoundEnabled = () => {
    const isMessageSound = !soundEnabled

    const settings = {
      userId: user?.id,
      isMessageSound
    }
    saveUserSettings(settings).then(() => {
      setSoundEnabled(isMessageSound);
      notify.success("Sound notification updated");
    })
  }

  const changeEmailEnabled = () => {
    const isReceiveEmail = !emailNotificationEnabled

    const settings = {
      userId: user?.id,
      isReceiveEmail
    }
    saveUserSettings(settings).then(() => {
      setEmailNotificationEnabled(isReceiveEmail);
      notify.success("Email notification updated");
    })
  }

  return (
    <div className="settings-page">
      <main className="settings-main">
        <div className="settings-container">
          <h1 className="settings-title">Settings</h1>
          <p className="settings-subtitle">
            Manage your account and preferences
          </p>

          <section className="settings-card">
            <h2 className="settings-card-title">Account Information</h2>

            <div className="settings-account">
              <div className="settings-avatar">
                {user?.avatar ? (
                  <img src={user.avatar} alt="avatar" />
                ) : (
                  getInitial()
                )}
              </div>

              <div className="settings-account-info">
                <div className="settings-account-name">
                  {user?.name || "—"}
                </div>

                <div className="settings-account-line">
                  <User size={15} />
                  <span>{user?.roleName || user?.role || "User"}</span>
                </div>

                <div className="settings-account-line">
                  <Mail size={15} />
                  <span>{user?.email || "—"}</span>
                </div>
              </div>
            </div>

            <div className="settings-password-row">
              <div className="settings-password-info">
                <div className="settings-password-label">
                  <Lock size={15} />
                  <span>Password</span>
                </div>
                <div className="settings-password-value">••••••••</div>
              </div>

              <button
                type="button"
                className="settings-change-password-button"
                onClick={openModal}
              >
                <KeyRound size={15} />
                Change Password
              </button>
            </div>
          </section>

          <section className="settings-card">
            <h2 className="settings-card-title">System Preferences</h2>

            <div className="settings-toggle-row">
              <div className="settings-toggle-info">
                <div className="settings-toggle-label">
                  <Volume2 size={17} />
                  <span>Notification Sound</span>
                </div>
                <p className="settings-toggle-desc">
                  Play a sound when a notification appears.
                </p>
              </div>

              <button
                type="button"
                role="switch"
                aria-checked={soundEnabled}
                className={`settings-switch ${soundEnabled ? "on" : ""}`}
                onClick={() => changeSoundEnabled()}
              >
                <span className="settings-switch-thumb" />
              </button>
            </div>

            <div className="settings-toggle-row">
              <div className="settings-toggle-info">
                <div className="settings-toggle-label">
                  <Bell size={17} />
                  <span>Email Notifications</span>
                </div>
                <p className="settings-toggle-desc">
                  Receive updates and reminders by email.
                </p>
              </div>

              <button
                type="button"
                role="switch"
                aria-checked={emailNotificationEnabled}
                className={`settings-switch ${
                  emailNotificationEnabled ? "on" : ""
                }`}
                onClick={() => changeEmailEnabled()}
              >
                <span className="settings-switch-thumb" />
              </button>
            </div>
          </section>
        </div>
      </main>

      <BaseModal
        isOpen={modalOpen}
        title="Change Password"
        onClose={closeModal}
      >
        <ChangePasswordModal
            onClose={closeModal}
            onSendCode={getEmailVerificationCode}
            onChangePassword={resetOwnPassword}
        />
      </BaseModal>
    </div>
  );
}
