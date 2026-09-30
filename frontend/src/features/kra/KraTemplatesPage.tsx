import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Chip from '@mui/material/Chip';
import Collapse from '@mui/material/Collapse';
import IconButton from '@mui/material/IconButton';
import LinearProgress from '@mui/material/LinearProgress';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import AddOutlined from '@mui/icons-material/AddOutlined';
import EditOutlined from '@mui/icons-material/EditOutlined';
import ExpandMoreOutlined from '@mui/icons-material/ExpandMoreOutlined';
import PublishOutlined from '@mui/icons-material/PublishOutlined';
import UnpublishedOutlined from '@mui/icons-material/UnpublishedOutlined';
import { toErrorMessage } from '@/api/axiosClient';
import { kraTemplateApi } from '@/api/kra';
import { EmptyState } from '@/components/EmptyState';
import { TableSkeleton } from '@/components/LoadingState';
import { PageHeader } from '@/components/PageHeader';
import type { KraTemplate } from '@/types/kra';
import { TemplateBuilderDialog } from './TemplateBuilderDialog';
import { formatNumber } from './ScorecardView';

export function KraTemplatesPage() {
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const [expanded, setExpanded] = useState<number | null>(null);
  const [builderOpen, setBuilderOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);

  const templates = useQuery({ queryKey: ['kra-templates'], queryFn: kraTemplateApi.list });

  const detail = useQuery({
    queryKey: ['kra-templates', expanded],
    queryFn: () => kraTemplateApi.get(expanded!),
    enabled: expanded !== null,
  });

  const publish = useMutation({
    mutationFn: ({ id, published }: { id: number; published: boolean }) =>
      kraTemplateApi.publish(id, published),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['kra-templates'] });
      setError(null);
    },
    onError: (caught) => setError(toErrorMessage(caught)),
  });

  const rows = templates.data ?? [];

  return (
    <Box>
      <PageHeader
        eyebrow="Performance & KRA"
        title="Scorecard templates"
        description="What each role is measured on. KRA weightages must total 100%, and so must the KPIs inside each KRA."
        actions={
          <Button
            variant="contained"
            startIcon={<AddOutlined sx={{ fontSize: 18 }} />}
            onClick={() => {
              setEditingId(null);
              setBuilderOpen(true);
            }}
          >
            New template
          </Button>
        }
      />

      {error && (
        <Alert severity="error" sx={{ mb: 4 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      <Card>
        {templates.isLoading ? (
          <TableSkeleton rows={5} columns={6} />
        ) : rows.length === 0 ? (
          <EmptyState
            title="No templates yet"
            description="Build a scorecard for a role, then publish it so it can be assigned."
          />
        ) : (
          <TableContainer sx={{ overflowX: 'auto' }}>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell width={40} />
                  <TableCell>Template</TableCell>
                  <TableCell>Applies to</TableCell>
                  <TableCell>Cycle</TableCell>
                  <TableCell align="right">KRAs</TableCell>
                  <TableCell align="right">KPIs</TableCell>
                  <TableCell>Weightage</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((template) => (
                  <TemplateRow
                    key={template.id}
                    template={template}
                    expanded={expanded === template.id}
                    detail={expanded === template.id ? detail.data : undefined}
                    onToggle={() => setExpanded(expanded === template.id ? null : template.id)}
                    onEdit={() => {
                      setEditingId(template.id);
                      setBuilderOpen(true);
                    }}
                    onPublish={(published) => publish.mutate({ id: template.id, published })}
                  />
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>

      <TemplateBuilderDialog
        open={builderOpen}
        templateId={editingId}
        onClose={() => {
          setBuilderOpen(false);
          setEditingId(null);
        }}
        onSaved={() => {
          void queryClient.invalidateQueries({ queryKey: ['kra-templates'] });
        }}
      />
    </Box>
  );
}

function TemplateRow({
  template,
  expanded,
  detail,
  onToggle,
  onEdit,
  onPublish,
}: {
  template: KraTemplate;
  expanded: boolean;
  detail?: KraTemplate;
  onToggle: () => void;
  onEdit: () => void;
  onPublish: (published: boolean) => void;
}) {
  const balanced = useMemo(
    () => Math.abs(Number(template.weightageTotal) - 100) < 0.01,
    [template.weightageTotal],
  );

  return (
    <>
      <TableRow hover>
        <TableCell>
          <IconButton
            size="small"
            onClick={onToggle}
            aria-label={expanded ? 'Hide detail' : 'Show detail'}
            sx={{ transform: expanded ? 'rotate(180deg)' : 'none', transition: 'transform 120ms' }}
          >
            <ExpandMoreOutlined sx={{ fontSize: 17 }} />
          </IconButton>
        </TableCell>
        <TableCell>
          <Typography sx={{ fontSize: 13, fontWeight: 600 }}>{template.name}</Typography>
          <Typography variant="caption" sx={{ color: 'text.secondary' }}>
            {template.code}
          </Typography>
        </TableCell>
        <TableCell>
          {template.designationName ?? 'Any designation'}
          <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block' }}>
            {template.departmentName ?? 'All departments'}
          </Typography>
        </TableCell>
        <TableCell sx={{ textTransform: 'capitalize' }}>
          {template.periodType.toLowerCase()}
        </TableCell>
        <TableCell align="right" className="tabular">
          {template.kraCount}
        </TableCell>
        <TableCell align="right" className="tabular">
          {template.kpiCount}
        </TableCell>
        <TableCell>
          <Chip
            size="small"
            color={balanced ? 'success' : 'warning'}
            variant={balanced ? 'filled' : 'outlined'}
            label={`${formatNumber(template.weightageTotal)}%`}
          />
        </TableCell>
        <TableCell>
          <Chip
            size="small"
            variant={template.published ? 'filled' : 'outlined'}
            color={template.published ? 'primary' : 'default'}
            label={template.published ? 'published' : 'draft'}
          />
        </TableCell>
        <TableCell align="right">
          <Tooltip title="Edit">
            <IconButton size="small" onClick={onEdit}>
              <EditOutlined sx={{ fontSize: 17 }} />
            </IconButton>
          </Tooltip>
          <Tooltip title={template.published ? 'Unpublish' : 'Publish so it can be assigned'}>
            <IconButton size="small" onClick={() => onPublish(!template.published)}>
              {template.published ? (
                <UnpublishedOutlined sx={{ fontSize: 17 }} />
              ) : (
                <PublishOutlined sx={{ fontSize: 17 }} />
              )}
            </IconButton>
          </Tooltip>
        </TableCell>
      </TableRow>

      <TableRow>
        <TableCell colSpan={9} sx={{ py: 0, borderBottom: expanded ? undefined : 'none' }}>
          <Collapse in={expanded} unmountOnExit>
            <Box sx={{ py: 4 }}>
              {!detail ? (
                <LinearProgress />
              ) : (
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                  {detail.items.map((item) => (
                    <Box key={item.id ?? item.name}>
                      <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 2, mb: 1.5 }}>
                        <Typography variant="subtitle2">{item.name}</Typography>
                        <Chip
                          size="small"
                          variant="outlined"
                          label={`${formatNumber(item.weightage)}%`}
                          sx={{ height: 19, fontSize: 10.5 }}
                        />
                        <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                          {item.kpis.length} KPIs · {formatNumber(item.kpiWeightageTotal)}% allocated
                        </Typography>
                      </Box>
                      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, pl: 3 }}>
                        {item.kpis.map((kpi) => (
                          <Chip
                            key={kpi.id ?? kpi.name}
                            size="small"
                            variant="outlined"
                            label={`${kpi.name} · ${formatNumber(kpi.weightage)}%${
                              kpi.targetDirection === 'LOWER_IS_BETTER' ? ' ↓' : ''
                            }`}
                            sx={{ height: 22 }}
                          />
                        ))}
                      </Box>
                    </Box>
                  ))}
                </Box>
              )}
            </Box>
          </Collapse>
        </TableCell>
      </TableRow>
    </>
  );
}
