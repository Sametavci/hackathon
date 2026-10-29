import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import type { ConnectionItem } from '../../api/types';
import ConnectionCard from './ConnectionCard';

const person: ConnectionItem = {
  participantId: 'participant-2', displayName: 'Sam', myChoice: null,
  status: 'NONE', myContact: null, theirContact: null,
};

function render(overrides: Partial<ConnectionItem>): string {
  return renderToStaticMarkup(<ConnectionCard item={{ ...person, ...overrides }} groupId="group-1"
    disabled={false} onUpdate={() => {}} onBusyChange={() => {}} onStale={() => {}} />);
}

describe('connection privacy presentation', () => {
  it('hides their contact and the sharing form unless status is mutual', () => {
    for (const status of ['NONE', 'WAITING_FOR_THEM'] as const) {
      const html = render({ status, theirContact: 'hidden-peer-contact' });
      expect(html).not.toContain('hidden-peer-contact');
      expect(html).not.toContain('<form');
      expect(html).toContain('aria-pressed="false"');
    }
  });

  it('allows withdrawal of my retained contact when the peer has withdrawn their choice', () => {
    const html = render({ status: 'WAITING_FOR_THEM', myChoice: 'YES', myContact: 'my-retained-contact' });
    expect(html).toContain('my-retained-contact');
    expect(html).toContain('Withdraw contact');
    expect(html).toContain('Your contact is hidden');
    expect(html).not.toContain('<form');
    expect(html).toContain('aria-pressed="true"');
  });

  it('labels the mutual sharing form and renders free text as text', () => {
    const html = render({ status: 'MUTUAL', myChoice: 'YES', theirContact: '<script>example</script>' });
    expect(html).toContain('for="contact-participant-2"');
    expect(html).toContain('maxLength="100"');
    expect(html).toContain('aria-describedby="hint-participant-2"');
    expect(html).toContain('&lt;script&gt;example&lt;/script&gt;');
    expect(html).not.toContain('<script>');
  });

  it('renders departed participants without contact details or controls', () => {
    const html = render({ displayName: 'Former participant', status: 'MUTUAL', myContact: 'old-own', theirContact: 'old-peer' });
    expect(html).toContain('This participant has left Lunch');
    expect(html).not.toContain('old-own');
    expect(html).not.toContain('old-peer');
    expect(html).not.toContain('<button');
    expect(html).not.toContain('<input');
  });
});
