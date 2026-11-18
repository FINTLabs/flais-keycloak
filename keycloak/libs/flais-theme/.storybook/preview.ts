import type { Preview } from "@storybook/react-vite";
import { useEffect } from "react";

const preview: Preview = {
  globalTypes: {
    theme: {
      description: "Login color scheme",
      toolbar: {
        icon: "paintbrush",
        dynamicTitle: true,
        items: [
          { value: "system", title: "System theme" },
          { value: "light", title: "Light theme" },
          { value: "dark", title: "Dark theme" },
        ],
      },
    },
  },
  initialGlobals: { theme: "system" },
  decorators: [
    (Story, context) => {
      const theme = context.globals.theme;
      useEffect(() => {
        if (theme === "light" || theme === "dark") {
          document.documentElement.dataset.theme = theme;
        } else {
          delete document.documentElement.dataset.theme;
        }
        return () => {
          delete document.documentElement.dataset.theme;
        };
      }, [theme]);
      return Story();
    },
  ],
  parameters: {
    controls: {
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/i,
      },
    },
  },
};

export default preview;
