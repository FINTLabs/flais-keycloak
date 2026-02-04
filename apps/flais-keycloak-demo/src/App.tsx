import "./App.css";
import Keycloak from "keycloak-js";
import { memo, type FC } from "react";
import ClaimsViewer from "./components/claimsViewer";

interface IAppProps {
    keycloak: Keycloak;
}

const App: FC<IAppProps> = ({ keycloak }) => {
    const handleLogout = () => {
        keycloak.logout();
    };

    return (
        <>
            <nav className="navbar">
                <div className="navbar-spacer" />
                <button className="logout-button" onClick={handleLogout}>
                    Logout
                </button>
            </nav>

            <ClaimsViewer keycloak={keycloak} />
        </>
    );
};

export default memo(App);
