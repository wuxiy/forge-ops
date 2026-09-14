import type { FeedbackCore, FeedbackView } from '@forgeops/feedback-core'

export interface MountedWidget {
  open(): void
  close(): void
  destroy(): void
}

/** Small native-DOM surface; host identity comes only from the bearer token, never a text field. */
export function mountWidget(container: HTMLElement, core: FeedbackCore): MountedWidget {
  let open = false
  let destroyed = false
  let mine: FeedbackView[] = []
  let error = ''
  let title = ''
  let description = ''

  const render = (): void => {
    if (destroyed) return
    container.innerHTML = open
      ? `<section data-forgeops-v2="panel">
          <button type="button" data-action="close">Close</button>
          <h2>Feedback</h2>
          <label>Title <input data-field="title" maxlength="200" value="${escape(title)}"></label>
          <label>Description <textarea data-field="description" maxlength="8000">${escape(description)}</textarea></label>
          <button type="button" data-action="submit">Submit</button>
          <button type="button" data-action="mine">My feedback</button>
          ${error ? `<p role="alert">${escape(error)}</p>` : ''}
          <ul>${mine.map((item) => `<li>${escape(item.id)} #${item.displayNo} ${escape(item.state)}</li>`).join('')}</ul>
        </section>`
      : '<button type="button" data-action="open">Feedback</button>'
    bind()
  }

  const bind = (): void => {
    container.querySelectorAll<HTMLInputElement | HTMLTextAreaElement>('[data-field]').forEach((element) => {
      element.addEventListener('input', () => {
        if (element.dataset.field === 'title') title = element.value
        if (element.dataset.field === 'description') description = element.value
      })
    })
    container.querySelectorAll<HTMLButtonElement>('[data-action]').forEach((button) => {
      button.addEventListener('click', async () => {
        try {
          switch (button.dataset.action) {
            case 'open': open = true; break
            case 'close': open = false; break
            case 'submit':
              if (!title.trim() || !description.trim()) throw new Error('Title and description are required')
              await core.submit({ title, description })
              title = ''
              description = ''
              mine = await core.mine()
              break
            case 'mine': mine = await core.mine(); break
          }
          error = ''
        } catch (failure) {
          error = failure instanceof Error ? failure.message : 'Feedback request failed'
        }
        render()
      })
    })
  }

  render()
  return {
    open: () => { open = true; render() },
    close: () => { open = false; render() },
    destroy: () => { destroyed = true; container.innerHTML = '' },
  }
}

function escape(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}
