type LogoProps = {
  width?: number | string;
  className?: string;
};

export default function Logo({ width = 'clamp(132px, 15vw, 166px)', className }: LogoProps) {
  return (
    <img
      src="/nowait-logo.svg"
      alt="Nowait"
      className={className}
      style={{
        display: 'block',
        width,
        height: 'auto',
        maxWidth: '100%',
      }}
    />
  );
}
