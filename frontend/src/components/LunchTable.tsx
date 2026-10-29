export default function LunchTable() {
  return <div aria-hidden="true" className="lunch-scene mx-auto w-full max-w-sm lg:max-w-none">
    <div className="lunch-orbit" />
    <div className="lunch-seat lunch-seat-top"><span className="lunch-face" /></div>
    <div className="lunch-seat lunch-seat-right"><span className="lunch-face" /></div>
    <div className="lunch-seat lunch-seat-bottom"><span className="lunch-face" /></div>
    <div className="lunch-seat lunch-seat-left"><span className="lunch-face" /></div>
    <div className="lunch-table"><span className="lunch-plate lunch-plate-one" /><span className="lunch-plate lunch-plate-two" /><span className="lunch-plate lunch-plate-three" /><span className="lunch-plate lunch-plate-four" /><span className="lunch-flower">✳</span></div>
  </div>;
}
