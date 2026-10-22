import "./index.css";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useUserStore } from "@/config/store/useUserStore";
import { login } from "@/config/request/api/userApi.js";
import { notify } from "@/utils/notify";
import {Moon, Sun} from "lucide-react";
import {useThemeStore} from "@/config/store/useThemeStore.js";
import {EMAIL_REGEX} from "@/config/constant/RegexPatternConstant.js";

function LoginPage() {
  const navigate = useNavigate();
  const setUserInfo = useUserStore((state) => state.setUserInfo);

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [loginButtonLoading, setLoginButtonLoading] = useState(false);
  // Toggle Light/Dark theme
  const {isDarkMode, toggleTheme} = useThemeStore();

  const handleSubmit = async (event) => {
    event.preventDefault();

    if (loginButtonLoading) return;

    const trimmedEmail = email.trim();
    const trimmedPassword = password.trim();

    if (!trimmedEmail || !trimmedPassword) {
      notify.warning("Please enter your email address and password")
      setError("Please enter your email address and password");
      return;
    }

    if (!EMAIL_REGEX.test(trimmedEmail)) {
      notify.warning("Please enter a valid email address")
      setError("Please enter a valid email address");
      return;
    }

    setLoginButtonLoading(true);
    setError("");

    try {
      const res = await login(trimmedEmail, trimmedPassword);
      const user = res.data;
      const roleId = String(user.roleId);

      setUserInfo({
        ...user,
        roleId,
      });

      notify.success(`Hi ${user.name}, Welcome to UNSW WBA Platform!`);

      switch (roleId) {
        case "1":
          navigate("/home/super-admin/dashboard", { replace: true });
          break;

        case "2":
          navigate("/home/admin/dashboard", { replace: true });
          break;

        case "3":
          navigate("/home/supervisor/dashboard", { replace: true });
          break;

        case "4":
          navigate("/home/student/dashboard", { replace: true });
          break;

        default:
          setError(`Unknown roleId: ${roleId}`);
          setLoginButtonLoading(false);
      }
    } catch (err) {
      setError(err?.message || "Unable to sign in. Please try again.");
      setLoginButtonLoading(false);
    }
  };

  const clearError = () => {
    if (error) {
      setError("");
    }
  };

  return (
    <main className="login-page">
      <button
          onClick={toggleTheme}
          className="login-header-icon-btn"
          title="Toggle Theme"
      >
        {isDarkMode ? <Sun size={20}/> : <Moon size={20}/>}
      </button>

      <section className="login-left" aria-label="WBA Platform introduction">
        <div className="yellow-slice" aria-hidden="true" />

        <div className="login-brand">
          <img
            src="/unsw-shield.svg"
            alt="UNSW"
            className="login-brand-logo"
          />

          <div>
            <h1 className="login-logo-title">WBA Platform</h1>
            <p className="login-logo-subtitle">UNSW Medicine &amp; Health</p>
          </div>
        </div>

        <div className="login-hero">
          <h2>
            Workplace-Based
            <br />
            Assessments,
            <br />
            <span>streamlined.</span>
          </h2>

          <div className="login-yellow-line" aria-hidden="true" />

          <p className="login-hero-description">
            A smarter way to manage
            <br />
            your assessments.
          </p>
        </div>
      </section>

      <section className="login-right">
        <div className="login-card">
          <h2 className="login-title">Sign in</h2>

          <p className="login-subtitle">
            Sign in with your account to continue
          </p>

          <form onSubmit={handleSubmit} noValidate>
            <div className="form-group">
              <label htmlFor="login-email">Email address</label>

              <input
                id="login-email"
                className="login-input"
                type="email"
                value={email}
                autoComplete="email"
                placeholder="Enter your email address"
                onChange={(event) => {
                  setEmail(event.target.value);
                  clearError();
                }}
              />
            </div>

            <div className="form-group password-group">
              <label htmlFor="login-password">Password</label>

              <div className="password-input-wrapper">
                <input
                  id="login-password"
                  type={showPassword ? "text" : "password"}
                  value={password}
                  autoComplete="current-password"
                  placeholder="Enter your password"
                  onChange={(event) => {
                    setPassword(event.target.value);
                    clearError();
                  }}
                />

                <button
                  type="button"
                  className="show-password-button"
                  aria-label={showPassword ? "Hide password" : "Show password"}
                  onClick={() => setShowPassword((current) => !current)}
                >
                  {showPassword ? "Hide" : "Show"}
                </button>
              </div>
            </div>

            <div className="forgot-password">
              <button type="button">Forgot password?</button>
            </div>

            {error && (
              <p className="error-message" role="alert">
                {error}
              </p>
            )}

            <button
              type="submit"
              className="login-submit-button"
              disabled={loginButtonLoading}
            >
              {loginButtonLoading ? (
                <>
                  <span className="login-button-spinner" aria-hidden="true" />
                  Signing in...
                </>
              ) : (
                "Sign in to WBA Platform"
              )}
            </button>
          </form>

          <div className="divider">
            <div />
            <span>Or continue with</span>
            <div />
          </div>

          <div className="social-login">
            <button type="button">Google</button>
            <button type="button">UNSW</button>
            <button type="button">Apple</button>
          </div>

          <p className="signup-text">
            Don&apos;t have an account? <span>Sign up for free</span>
          </p>
        </div>
      </section>
    </main>
  );
}

export default LoginPage;
