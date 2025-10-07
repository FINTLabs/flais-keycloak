import React from 'react'

export interface SubmitButtonProps {
  text: string
}

const SubmitButtonComponent = ({ text }: SubmitButtonProps) => {
  return (
    <button
      type="submit"
      className="w-full py-3 font-medium rounded-lg
             bg-primary text-white hover:bg-primary/90
             focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary"
    >
      {text}
    </button>
  )
}

export const SubmitButton = React.memo(SubmitButtonComponent)
