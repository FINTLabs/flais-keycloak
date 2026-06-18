import React, {
  type HTMLAttributes,
  type KeyboardEvent,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { Chevron, type ChevronDirection } from "./icons/Chevron";

export interface LogoOption {
  id: string;
  label: string;
  logosUrl?: string;
}

export interface LogoDropdownInputProps extends Omit<
  HTMLAttributes<HTMLDivElement>,
  "onChange"
> {
  name: string;
  options: LogoOption[];
  value: string | null;
  onChange: (id: string) => void;
  placeholder?: string;
}

const KEY = {
  Down: "ArrowDown",
  Up: "ArrowUp",
  Enter: "Enter",
  Escape: "Escape",
  Space: " ",
  Tab: "Tab",
} as const;

const TRIGGER_OPEN_KEYS: readonly string[] = [
  KEY.Down,
  KEY.Up,
  KEY.Enter,
  KEY.Space,
];

const getNextIndex = (
  currentIndex: number,
  direction: 1 | -1,
  count: number,
) => {
  if (count === 0) {
    return -1;
  }

  return (currentIndex + direction + count) % count;
};

const LogoDropdownInputComponent = ({
  id,
  name,
  options,
  value,
  onChange,
  placeholder = "Velg tilhørighet",
  className,
  ...rest
}: LogoDropdownInputProps) => {
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const optionRefs = useRef<(HTMLButtonElement | null)[]>([]);

  const controlId = id ?? name;
  const listboxId = `${controlId}-listbox`;
  const selected = useMemo(
    () => options.find((option) => option.id === value) ?? null,
    [options, value],
  );
  const [chevronDirection, setChevronDirection] =
    useState<ChevronDirection>("down");

  useEffect(() => {
    const handlePointerDown = (event: MouseEvent) => {
      if (!containerRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    };

    document.addEventListener("mousedown", handlePointerDown);
    return () => document.removeEventListener("mousedown", handlePointerDown);
  }, []);

  useEffect(() => {
    if (open) {
      const selectedIndex = options.findIndex((option) => option.id === value);
      optionRefs.current[Math.max(selectedIndex, 0)]?.focus();
    }
  }, [open, options, value]);

  const selectOption = (optionId: string) => {
    onChange(optionId);
    setOpen(false);
    setChevronDirection("down");
  };

  const focusOption = (direction: 1 | -1) => {
    const currentIndex = optionRefs.current.findIndex(
      (element) => element === document.activeElement,
    );
    const nextIndex = getNextIndex(currentIndex, direction, options.length);

    if (nextIndex >= 0) {
      optionRefs.current[nextIndex]?.focus();
    }
  };

  const handleTriggerKeyDown = (event: KeyboardEvent<HTMLButtonElement>) => {
    if (TRIGGER_OPEN_KEYS.includes(event.key)) {
      event.preventDefault();
      setOpen(true);
    }
  };

  const handleListboxKeyDown = (event: KeyboardEvent<HTMLUListElement>) => {
    if (event.key === KEY.Down) {
      event.preventDefault();
      focusOption(1);
    }

    if (event.key === KEY.Up) {
      event.preventDefault();
      focusOption(-1);
    }

    if (event.key === KEY.Escape || event.key === KEY.Tab) {
      setOpen(false);
    }
  };

  return (
    <div
      ref={containerRef}
      className={`relative w-full text-left ${className ?? ""}`}
      {...rest}
    >
      <input type="hidden" name={name} value={value ?? ""} />

      <button
        id={controlId}
        type="button"
        role="combobox"
        aria-controls={listboxId}
        aria-expanded={open}
        aria-haspopup="listbox"
        onClick={() => {
          setChevronDirection(chevronDirection == "up" ? "down" : "up");
          setOpen((isOpen) => !isOpen);
        }}
        onKeyDown={handleTriggerKeyDown}
        className="
    flex h-12 w-full items-center gap-2 rounded-md text-base
    border border-gray-300 bg-white px-3 text-left text-gray-700
    hover:bg-gray-50
    focus:z-10 focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary
  "
      >
        {selected?.logosUrl && (
          <span className="flex h-8 w-8 shrink-0 items-center justify-center overflow-hidden rounded-full">
            <img
              src={selected.logosUrl}
              alt=""
              aria-hidden="true"
              className="max-h-full max-w-full object-contain"
            />
          </span>
        )}

        <span
          className={
            selected
              ? "min-w-0 flex-1 truncate"
              : "min-w-0 flex-1 truncate text-gray-400"
          }
        >
          {selected?.label ?? placeholder}
        </span>

        <Chevron
          direction={chevronDirection}
          aria-hidden="true"
          className="h-4 w-4 shrink-0 text-gray-500"
        />
      </button>

      {open && (
        <ul
          id={listboxId}
          role="listbox"
          aria-labelledby={controlId}
          tabIndex={-1}
          onKeyDown={handleListboxKeyDown}
          className="
            absolute z-10 mt-1 max-h-60 w-full overflow-auto rounded-md bg-white shadow-lg
            ring-1 ring-black ring-opacity-5 focus:outline-none
          "
        >
          {options.map((option, index) => {
            const isSelected = option.id === value;

            return (
              <li key={option.id} role="presentation">
                <button
                  ref={(element) => {
                    optionRefs.current[index] = element;
                  }}
                  id={`${listboxId}-${option.id}`}
                  type="button"
                  role="option"
                  aria-selected={isSelected}
                  onClick={() => selectOption(option.id)}
                  className={`
                    flex w-full items-center px-4 py-2 text-left text-base
                    focus:outline-none focus:bg-gray-200
                    ${
                      isSelected
                        ? "bg-gray-100 font-semibold text-gray-900"
                        : "text-gray-700 hover:bg-gray-50"
                    }
                  `}
                >
                  {option.logosUrl && (
                    <span className="mr-3 flex h-8 w-8 shrink-0 items-center justify-center overflow-hidden rounded-full">
                      <img
                        src={option.logosUrl}
                        alt=""
                        aria-hidden="true"
                        className="max-h-full max-w-full object-contain"
                      />
                    </span>
                  )}
                  <span className="truncate">{option.label}</span>
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
};

export const LogoDropdownInput = React.memo(LogoDropdownInputComponent);
