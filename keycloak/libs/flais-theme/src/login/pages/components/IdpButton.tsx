import React from 'react'

export interface IdpButtonProps {
  alias: string
  name: string
}

const IdpButtonComponent = ({ alias, name }: IdpButtonProps) => {
  return (
    <button
      type="submit"
      name="identity_provider"
      value={alias}
      className="
        w-full flex items-center justify-between
        px-4 py-3
        bg-white
        hover:bg-gray-50
        active:bg-gray-100
        focus:outline-none focus:ring-2 focus:ring-inset focus:ring-primary
        transition-colors duration-150 ease-in-out
        cursor-pointer
        rounded-none
      "
    >
      <span className="text-accent font-medium">{name}</span>
      <svg
        className="h-5 w-5 text-gray-400 flex-shrink-0"
        fill="none"
        stroke="currentColor"
        viewBox="0 0 24 24"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={2}
          d="M9 5l7 7-7 7"
        />
      </svg>
    </button>
  )
}

export const IdpButton = React.memo(IdpButtonComponent)
