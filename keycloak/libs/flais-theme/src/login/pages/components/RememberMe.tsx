const RememberMe = () => (
  <div className="flex items-center">
    <input
      name="remember_me"
      id="remember"
      type="checkbox"
      className="h-4 w-4 text-primary focus:ring-primary rounded"
    />
    <label htmlFor="remember" className="ml-2 text-sm text-accent">
      Husk meg
    </label>
  </div>
)

export default RememberMe
