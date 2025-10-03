import React, {
  useState,
  useRef,
  useEffect,
  KeyboardEvent,
  HTMLAttributes,
} from 'react'
import ArrowDown from './icons/ArrowDown'

export interface ImageOption {
  id: string
  label: string
  imageUrl?: string
}

interface ImageDropdownInputProps
  extends Omit<HTMLAttributes<HTMLDivElement>, 'onChange'> {
  name: string
  options: ImageOption[]
  value: string | null
  onChange: (id: string) => void
  placeholder?: string
}

const ImageDropdownInput: React.FC<ImageDropdownInputProps> = ({
  name,
  options,
  value,
  onChange,
  placeholder = 'Velg tilhørighet',
  className,
  ...rest
}) => {
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const itemsRef = useRef<(HTMLButtonElement | null)[]>([])

  // close on outside click
  useEffect(() => {
    const onClick = (e: MouseEvent) => {
      if (
        containerRef.current &&
        !containerRef.current.contains(e.target as Node)
      ) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', onClick)
    return () => document.removeEventListener('mousedown', onClick)
  }, [])

  // keyboard nav
  const onKeyDown = (e: KeyboardEvent) => {
    if (!open) return
    const idx = itemsRef.current.findIndex(
      (el) => el === document.activeElement
    )
    let next = idx
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      next = (idx + 1) % itemsRef.current.length
      itemsRef.current[next]?.focus()
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault()
      next = (idx - 1 + itemsRef.current.length) % itemsRef.current.length
      itemsRef.current[next]?.focus()
    }
    if (['Escape', 'Tab'].includes(e.key)) {
      setOpen(false)
    }
  }

  const selected = options.find((o) => o.id === value) ?? null

  return (
    <div
      ref={containerRef}
      className={`relative w-full text-left ${className || ''}`}
      {...rest}
    >
      {/* ========== trigger ========== */}
      <div className="flex">
        {/* visible “input” */}
        <input
          type="text"
          readOnly
          value={selected?.label || ''}
          placeholder={selected ? undefined : placeholder}
          onClick={() => setOpen((o) => !o)}
          aria-haspopup="listbox"
          aria-expanded={open}
          className="
            flex-1 h-10 px-3
            border border-gray-300
            rounded-l-md
            bg-white text-gray-700 placeholder-gray-400
            cursor-pointer
            focus:z-10 focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary
          "
        />

        {/* arrow button */}
        <button
          type="button"
          onClick={() => setOpen((o) => !o)}
          aria-label="Toggle dropdown"
          className="
            flex h-10 w-10 items-center justify-center
            border-t border-b border-r border-gray-300
            bg-white
            rounded-r-md
            hover:bg-gray-50
            focus:z-10 focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary
          "
        >
          <ArrowDown className="text-gray-500" />
        </button>
      </div>

      {/* real form value */}
      <input type="hidden" name={name} value={value || ''} />

      {/* ========== menu ========== */}
      {open && (
        <ul
          role="listbox"
          tabIndex={-1}
          onKeyDown={onKeyDown}
          className="
            absolute z-10 mt-1 w-full bg-white rounded-md shadow-lg
            ring-1 ring-black ring-opacity-5 max-h-60 overflow-auto focus:outline-none
          "
        >
          {options.map((opt, idx) => {
            const isSel = opt.id === value
            return (
              <li key={opt.id} role="option" aria-selected={isSel}>
                <button
                  ref={(el) => {
                    itemsRef.current[idx] = el
                  }}
                  onClick={() => {
                    onChange(opt.id)
                    setOpen(false)
                  }}
                  className={`
                    flex items-center w-full px-4 py-2 text-left text-base
                    ${
                      isSel
                        ? 'bg-gray-100 font-semibold text-gray-900'
                        : 'text-gray-700 hover:bg-gray-50'
                    }
                    focus:outline-none focus:bg-gray-200
                  `}
                >
                  {opt.imageUrl && (
                    <img
                      src={opt.imageUrl}
                      alt=""
                      className="h-8 w-8 rounded-full object-cover mr-3 flex-shrink-0"
                    />
                  )}
                  <span className="truncate">{opt.label}</span>
                </button>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}

export default ImageDropdownInput
