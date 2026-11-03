import { cn } from "@/lib/utils"

interface LogoProps {
  className?: string
  iconClassName?: string
  textClassName?: string
}

export function Logo({ className, iconClassName, textClassName }: LogoProps) {
  return (
    <div className={cn("flex items-center gap-2", className)}>
      <img src="/scout.svg" alt="" className={cn("size-9", iconClassName)} />
      <span
        className={cn(
          "font-heading text-2xl font-semibold tracking-tight text-foreground",
          textClassName
        )}
      >
        Scout
      </span>
    </div>
  )
}
