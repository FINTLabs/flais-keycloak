interface SubmitButtonProps {
  text?: string
}

const SubmitButton = ({ text = 'FORTSETT' }: SubmitButtonProps) => (
  <button
    type="submit"
    className="w-full py-3 font-medium rounded-lg
             bg-primary text-white hover:bg-primary/90
             focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary"
  >
    {text}
  </button>
)

export default SubmitButton
