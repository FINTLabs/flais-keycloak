interface LoginHeaderProps {
  title: string
  subtitle: string
}

const LoginHeader = ({ title, subtitle }: LoginHeaderProps) => (
  <div className="text-center space-y-2">
    <h2 className="text-3xl font-semibold text-accent">{title}</h2>
    <p className="text-sm text-accent/80">{subtitle}</p>
  </div>
)

export default LoginHeader
